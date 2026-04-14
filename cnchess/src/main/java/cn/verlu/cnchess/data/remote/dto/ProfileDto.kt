package cn.verlu.cnchess.data.remote.dto

import cn.verlu.cnchess.domain.model.Profile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val email: String? = null,
)

fun ProfileDto.toDomain(): Profile = Profile(
    id = id,
    displayName = displayName.ifBlank { email?.substringBefore("@") ?: "未知用户" },
    avatarUrl = avatarUrl,
    email = email,
)
