package cn.verlu.talk.data.repository

import cn.verlu.talk.domain.model.Profile

interface ProfileRepository {
    suspend fun getProfile(userId: String): Profile?
    suspend fun searchProfiles(query: String): List<Profile>
    suspend fun getCurrentUserProfile(): Profile?
}
