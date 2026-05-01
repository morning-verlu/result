package cn.verlu.lulu.domain.cloud

import java.time.Instant

data class CloudFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val updatedAt: Instant,
)
