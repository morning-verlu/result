/**
 * 本草 API 中转：验证 Supabase 用户 JWT 后，用服务端环境变量中的 HERB_API_KEY 请求 zyapi。
 *
 * 部署后在 Dashboard → Edge Functions → Secrets 设置：
 *   HERB_API_KEY   = 与 zyapi 服务端 API_KEY 一致
 *   HERB_API_BASE  = https://zyapi.withgo.cn（可选，默认同左）
 *
 * 调用方式：GET .../functions/v1/herb-proxy?p=/health 或 ?p=/stats
 *   其它查询参数（除 p 外）会原样附加到上游 URL。
 *   Authorization: Bearer <用户 access_token>
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders })
  }

  if (req.method !== "GET") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    })
  }

  try {
    const authHeader = req.headers.get("Authorization")
    const token = authHeader?.replace(/^Bearer\s+/i, "") ?? ""
    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    )

    const { data: { user }, error: userErr } = await supabaseAdmin.auth.getUser(token)
    if (userErr || !user) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      })
    }

    const url = new URL(req.url)
    const herbPath = url.searchParams.get("p")
    if (!herbPath || !herbPath.startsWith("/")) {
      return new Response(
        JSON.stringify({ error: "Missing or invalid query parameter p (must start with /)" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      )
    }

    const base = (Deno.env.get("HERB_API_BASE") ?? "https://zyapi.withgo.cn").replace(
      /\/+$/,
      "",
    )
    const apiKey = Deno.env.get("HERB_API_KEY") ?? ""

    const target = new URL(base + herbPath)
    url.searchParams.forEach((value, key) => {
      if (key !== "p") target.searchParams.append(key, value)
    })

    const upstream = await fetch(target.toString(), {
      method: "GET",
      headers: {
        Accept: "application/json",
        ...(apiKey ? { "X-API-Key": apiKey } : {}),
      },
    })

    const body = await upstream.arrayBuffer()
    const ct = upstream.headers.get("content-type") ?? "application/json"
    return new Response(body, {
      status: upstream.status,
      headers: {
        ...corsHeaders,
        "Content-Type": ct,
      },
    })
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    return new Response(JSON.stringify({ error: msg }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    })
  }
})
