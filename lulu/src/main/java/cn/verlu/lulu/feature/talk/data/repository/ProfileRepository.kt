package cn.verlu.lulu.feature.talk.data.repository

import cn.verlu.lulu.feature.talk.domain.model.Profile

interface ProfileRepository {
    suspend fun getProfile(userId: String): Profile?
    suspend fun searchProfiles(query: String): List<Profile>
    suspend fun getCurrentUserProfile(): Profile?
}
