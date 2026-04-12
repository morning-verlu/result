# 存储与网盘架构说明

## 分层

| 层级 | 职责 | 替换服务商时 |
|------|------|----------------|
| **领域模型** `domain/storage` | `RemoteStorageObject` 等与厂商无关的元数据 | 一般不变 |
| **端口** `data/storage/ObjectStoragePort` | 列出/删除/上传（预留）对象的抽象 API | 不变：新后端实现同一接口 |
| **适配器** `data/storage/supabase`、`data/storage/s3` | Supabase Storage、S3 兼容端等具体实现 | 在此处增加/切换实现 |
| **文件仓库** `data/files/DefaultFileRepository` | 本地 SQLDelight 索引 + 调用 `ObjectStoragePort` 同步 | 通常不变，除非同步策略大改 |
| **DI** `di/AppGraph` | 组装 `ObjectStoragePort` 与 `FileRepository` | 改一行：注入 `BitifulS3Adapter` 等 |

业务 UI（如 `FileExplorerState`）只依赖 `FileRepository`，不直接依赖 Supabase 或 S3 SDK。

## 对象键约定

远程对象统一使用前缀隔离用户：

`owners/{userId}/...`

列表与删除均基于桶内完整路径（与 Supabase Storage / S3 key 一致）。

## Supabase

1. 在控制台 **Storage** 中创建与 `SupabaseConfig.STORAGE_BUCKET` 同名的 bucket（默认 `cloud-user-files`，可在代码中修改）。
2. 配置 **RLS / 存储策略**：仅允许用户读写自己前缀下的对象（例如 `owners/{auth.uid()}/`）。具体策略以 Supabase 文档为准。
3. 客户端已 `install(Storage)`，使用 `SupabaseObjectStorageAdapter`。

## 缤纷云 / 其他 S3 兼容服务

此类服务通常提供 **S3 API**（自定义 endpoint、Access Key、Secret、桶名）。

下一步实现：`data/storage/s3` 下实现 `ObjectStoragePort`，使用 AWS SDK for Kotlin 或自签 SigV4 + Ktor，将 `listObjectsV2` / `deleteObject` / `putObject` 映射到 `RemoteStorageObject` 与路径约定。`S3CompatibleObjectStorageStub` 为占位类。

## 认证与存储

当前认证仍可通过 Supabase Auth；若将来连认证也迁走，只需提供新的 `AuthRepository` 实现，与 `ObjectStoragePort` 解耦。若缤纷云同时提供 OIDC，可在应用层做统一会话后再访问 S3。

## 后续常见功能（建议顺序）

1. 上传队列消费：`TransferRepository` 与 `ObjectStoragePort.uploadBytes` 打通，失败重试与进度回写。
2. 下载与打开本地缓存：`offline_pin` 表 + 下载路径。
3. 分块与断点续传：在 S3/Supabase 适配器内实现。
4. 增量同步：对比本地索引与远程 etag/更新时间，而非全量 clear + insert。
