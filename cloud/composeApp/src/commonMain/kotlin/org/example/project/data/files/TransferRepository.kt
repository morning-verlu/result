package cn.verlu.cloud.data.files

import kotlinx.coroutines.flow.Flow
import cn.verlu.cloud.domain.files.TransferTaskItem

interface TransferRepository {
    fun observeTransfers(ownerId: String): Flow<List<TransferTaskItem>>
    suspend fun enqueueUpload(ownerId: String, localUri: String, remotePath: String): Result<Unit>
    suspend fun refreshTransfers(ownerId: String): Result<Unit>
}
