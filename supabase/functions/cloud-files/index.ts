import {
  S3Client,
  ListObjectsV2Command,
  DeleteObjectsCommand,
  CopyObjectCommand,
  DeleteObjectCommand,
  PutObjectCommand,
  GetObjectCommand,
  CreateMultipartUploadCommand,
  UploadPartCommand,
  CompleteMultipartUploadCommand,
  AbortMultipartUploadCommand,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { createClient } from "@supabase/supabase-js";

const rawEndpoint = (Deno.env.get("S3_ENDPOINT") || "s3.bitiful.net").trim();
const normalizedHost = rawEndpoint
  .replace(/^https?:\/\//i, "")
  .replace(/\/+$/, "");
const S3_ENDPOINT = `https://${normalizedHost}`;
const S3_REGION = Deno.env.get("S3_REGION") || "cn-east-1";
const S3_BUCKET = Deno.env.get("S3_BUCKET") || "cloud-kmp";
const S3_ACCESS_KEY = (Deno.env.get("S3_ACCESS_KEY") || "").trim();
const S3_SECRET_KEY = (Deno.env.get("S3_SECRET_KEY") || "").trim();
const S3_FORCE_PATH_STYLE =
  (Deno.env.get("S3_FORCE_PATH_STYLE") || "false").toLowerCase() === "true";

const s3VirtualHost = new S3Client({
  endpoint: S3_ENDPOINT,
  region: S3_REGION,
  credentials: {
    accessKeyId: S3_ACCESS_KEY,
    secretAccessKey: S3_SECRET_KEY,
  },
  forcePathStyle: false,
});

const s3PathStyle = new S3Client({
  endpoint: S3_ENDPOINT,
  region: S3_REGION,
  credentials: {
    accessKeyId: S3_ACCESS_KEY,
    secretAccessKey: S3_SECRET_KEY,
  },
  forcePathStyle: true,
});

function preferredClient(): S3Client {
  return S3_FORCE_PATH_STYLE ? s3PathStyle : s3VirtualHost;
}
function fallbackClient(): S3Client {
  return S3_FORCE_PATH_STYLE ? s3VirtualHost : s3PathStyle;
}
function isSignatureLikeError(e: unknown): boolean {
  const msg = String(e);
  return (
    msg.includes("SignatureDoesNotMatch") ||
    msg.includes("request signature we calculated does not match") ||
    msg.includes("Access Denied") ||
    msg.includes("AccessDenied")
  );
}
async function withS3Fallback<T>(op: (client: S3Client) => Promise<T>): Promise<T> {
  try {
    return await op(preferredClient());
  } catch (e) {
    if (!isSignatureLikeError(e)) throw e;
    return await op(fallbackClient());
  }
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

function jsonOk(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function jsonErr(msg: string, status = 400): Response {
  return new Response(JSON.stringify({ error: msg }), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();
    const { action } = body;
    if (!action) return jsonErr("Missing action");

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return jsonErr("Missing Authorization header", 401);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const token = authHeader.replace("Bearer ", "");
    const { data: { user }, error: userErr } = await supabase.auth.getUser(token);
    if (userErr || !user) return jsonErr("Unauthorized", 401);

    if (!S3_ACCESS_KEY || !S3_SECRET_KEY) {
      return jsonErr("Missing S3 credentials in Supabase function secrets", 500);
    }

    const userPrefix = `owners/${user.id}/`;

    switch (action as string) {
      /* ── list ── */
      case "list": {
        const prefix: string = body.prefix ?? "";
        const fullPrefix = userPrefix + prefix;

        const result = await withS3Fallback((client) =>
          client.send(
            new ListObjectsV2Command({
              Bucket: S3_BUCKET,
              Prefix: fullPrefix,
              Delimiter: "/",
              MaxKeys: 1000,
            }),
          ),
        );

        const files = (result.Contents ?? [])
          .filter((o) => o.Key !== fullPrefix)
          .map((o) => ({
            path: o.Key!.slice(userPrefix.length),
            name: o.Key!.split("/").pop() ?? o.Key!,
            sizeBytes: o.Size ?? 0,
            updatedAtMs: o.LastModified?.getTime() ?? 0,
            isDirectory: false,
            etag: (o.ETag ?? "").replace(/"/g, ""),
          }));

        const folders = (result.CommonPrefixes ?? []).map((p) => {
          const folderPath = p.Prefix!.slice(userPrefix.length);
          const name = folderPath.replace(prefix, "").replace(/\/$/, "");
          return {
            path: folderPath,
            name,
            sizeBytes: 0,
            updatedAtMs: 0,
            isDirectory: true,
            etag: null,
          };
        });

        return jsonOk({ objects: [...folders, ...files] });
      }

      /* ── upload-url ── */
      case "upload-url": {
        const { path } = body as {
          path: string;
        };
        if (!path) return jsonErr("Missing path");
        const fullKey = userPrefix + path;

        const url = await withS3Fallback((client) =>
          getSignedUrl(
            client,
            new PutObjectCommand({
              Bucket: S3_BUCKET,
              Key: fullKey,
            }),
            { expiresIn: 3600 },
          )
        );

        return jsonOk({ url });
      }

      /* ── multipart-init ── */
      case "multipart-init": {
        const { path, contentType } = body as {
          path: string;
          contentType?: string;
        };
        if (!path) return jsonErr("Missing path");
        const fullKey = userPrefix + path;
        const result = await withS3Fallback((client) =>
          client.send(
            new CreateMultipartUploadCommand({
              Bucket: S3_BUCKET,
              Key: fullKey,
              ContentType: contentType || "application/octet-stream",
            }),
          )
        );
        if (!result.UploadId) return jsonErr("Missing UploadId from S3", 500);
        return jsonOk({ uploadId: result.UploadId, key: fullKey });
      }

      /* ── multipart-part-url ── */
      case "multipart-part-url": {
        const { path, uploadId, partNumber } = body as {
          path: string;
          uploadId: string;
          partNumber: number;
        };
        if (!path) return jsonErr("Missing path");
        if (!uploadId) return jsonErr("Missing uploadId");
        if (!partNumber || partNumber < 1) return jsonErr("Invalid partNumber");
        const fullKey = userPrefix + path;
        const url = await withS3Fallback((client) =>
          getSignedUrl(
            client,
            new UploadPartCommand({
              Bucket: S3_BUCKET,
              Key: fullKey,
              UploadId: uploadId,
              PartNumber: partNumber,
            }),
            { expiresIn: 3600 },
          )
        );
        return jsonOk({ url });
      }

      /* ── multipart-complete ── */
      case "multipart-complete": {
        const { path, uploadId, parts } = body as {
          path: string;
          uploadId: string;
          parts: Array<{ partNumber: number; eTag: string }>;
        };
        if (!path) return jsonErr("Missing path");
        if (!uploadId) return jsonErr("Missing uploadId");
        if (!parts?.length) return jsonErr("Missing parts");
        const fullKey = userPrefix + path;
        await withS3Fallback((client) =>
          client.send(
            new CompleteMultipartUploadCommand({
              Bucket: S3_BUCKET,
              Key: fullKey,
              UploadId: uploadId,
              MultipartUpload: {
                Parts: parts
                  .filter((p) => p.partNumber > 0 && !!p.eTag)
                  .sort((a, b) => a.partNumber - b.partNumber)
                  .map((p) => ({
                    PartNumber: p.partNumber,
                    ETag: p.eTag,
                  })),
              },
            }),
          )
        );
        return jsonOk({ ok: true });
      }

      /* ── multipart-abort ── */
      case "multipart-abort": {
        const { path, uploadId } = body as {
          path: string;
          uploadId: string;
        };
        if (!path) return jsonErr("Missing path");
        if (!uploadId) return jsonErr("Missing uploadId");
        const fullKey = userPrefix + path;
        await withS3Fallback((client) =>
          client.send(
            new AbortMultipartUploadCommand({
              Bucket: S3_BUCKET,
              Key: fullKey,
              UploadId: uploadId,
            }),
          )
        );
        return jsonOk({ ok: true });
      }

      /* ── upload (server-side proxy, avoid client-side presign mismatch) ── */
      case "upload": {
        const { path, base64, contentType } = body as {
          path: string;
          base64: string;
          contentType?: string;
        };
        if (!path) return jsonErr("Missing path");
        if (!base64) return jsonErr("Missing base64");
        const fullKey = userPrefix + path;
        const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));

        await withS3Fallback((client) =>
          client.send(
            new PutObjectCommand({
              Bucket: S3_BUCKET,
              Key: fullKey,
              Body: bytes,
              ContentType: contentType || "application/octet-stream",
            }),
          )
        );
        return jsonOk({ ok: true });
      }

      /* ── download-url ── */
      case "download-url": {
        const { path, expiresInSeconds } = body as { path: string; expiresInSeconds?: number };
        if (!path) return jsonErr("Missing path");
        const fullKey = path.startsWith("owners/") ? path : userPrefix + path;
        if (!fullKey.startsWith(userPrefix)) return jsonErr("Forbidden", 403);
        const ttl = Math.max(60, Math.min(Number(expiresInSeconds ?? 3600), 604800));

        const url = await withS3Fallback((client) =>
          getSignedUrl(
            client,
            new GetObjectCommand({ Bucket: S3_BUCKET, Key: fullKey }),
            { expiresIn: ttl },
          )
        );

        return jsonOk({ url });
      }

      /* ── delete ── */
      case "delete": {
        const { paths } = body as { paths: string[] };
        if (!paths?.length) return jsonOk({ deleted: 0 });

        const keys = paths
          .map((p) => (p.startsWith("owners/") ? p : userPrefix + p))
          .filter((p) => p.startsWith(userPrefix));

        if (keys.length === 0) return jsonOk({ deleted: 0 });

        if (keys.length === 1) {
          await withS3Fallback((client) =>
            client.send(
              new DeleteObjectCommand({ Bucket: S3_BUCKET, Key: keys[0] }),
            )
          );
        } else {
          await withS3Fallback((client) =>
            client.send(
              new DeleteObjectsCommand({
                Bucket: S3_BUCKET,
                Delete: { Objects: keys.map((k) => ({ Key: k })) },
              }),
            )
          );
        }

        return jsonOk({ deleted: keys.length });
      }

      /* ── move ── */
      case "move": {
        const { from, to } = body as { from: string; to: string };
        if (!from || !to) return jsonErr("Missing from/to");

        const fromKey = from.startsWith("owners/") ? from : userPrefix + from;
        const toKey = to.startsWith("owners/") ? to : userPrefix + to;

        if (!fromKey.startsWith(userPrefix) || !toKey.startsWith(userPrefix)) {
          return jsonErr("Forbidden", 403);
        }

        await withS3Fallback((client) =>
          client.send(
            new CopyObjectCommand({
              Bucket: S3_BUCKET,
              CopySource: `${S3_BUCKET}/${fromKey}`,
              Key: toKey,
            }),
          )
        );
        await withS3Fallback((client) =>
          client.send(
            new DeleteObjectCommand({ Bucket: S3_BUCKET, Key: fromKey }),
          )
        );

        return jsonOk({ ok: true });
      }

      default:
        return jsonErr(`Unknown action: ${action}`);
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("cloud-files error:", msg);
    return jsonErr(msg, 500);
  }
});
