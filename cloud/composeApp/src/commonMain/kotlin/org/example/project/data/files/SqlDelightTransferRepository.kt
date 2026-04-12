package cn.verlu.cloud.data.files

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import cn.verlu.cloud.db.CloudDatabase
import cn.verlu.cloud.domain.files.TransferTaskItem

class SqlDelightTransferRepository(
    private val database: CloudDatabase,
) : TransferRepository {
    override fun observeTransfers(ownerId: String): Flow<List<TransferTaskItem>> =
        database.cloudTablesQueries.selectAllTransfers(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list ->
                list.map { row ->
                    TransferTaskItem(
                        id = row.id,
                        ownerId = row.owner_id,
                        fileId = row.file_id,
                        remotePath = row.remote_path,
                        direction = row.direction,
                        status = row.status,
                        transferredBytes = row.transferred_bytes,
                        totalBytes = row.total_bytes,
                        updatedAtMs = row.updated_at_ms,
                    )
                }
            }

    override suspend fun enqueueUpload(ownerId: String, localUri: String, remotePath: String): Result<Unit> =
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            database.cloudTablesQueries.replaceTransfer(
                id = "task-${Random.nextLong().toString(16)}",
                owner_id = ownerId,
                file_id = null,
                local_uri = localUri,
                remote_path = remotePath,
                direction = "upload",
                status = "queued",
                transferred_bytes = 0,
                total_bytes = 0,
                resume_token = null,
                created_at_ms = now,
                updated_at_ms = now,
            )
        }

    override suspend fun refreshTransfers(ownerId: String): Result<Unit> = runCatching {
        // 基础阶段：后续对�?Supabase cloud_transfers 做静默对账�?
        ownerId
    }.map { Unit }
}
