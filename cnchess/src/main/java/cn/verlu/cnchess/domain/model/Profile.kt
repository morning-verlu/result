package cn.verlu.cnchess.domain.model

data class Profile(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val email: String? = null,
)
