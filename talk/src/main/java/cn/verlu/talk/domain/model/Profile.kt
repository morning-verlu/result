package cn.verlu.talk.domain.model

data class Profile(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val email: String? = null,
)
