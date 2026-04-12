package cn.verlu.talk.data.repository

import cn.verlu.talk.data.remote.dto.ProfileDto
import cn.verlu.talk.data.remote.dto.toDomain
import cn.verlu.talk.di.IoDispatcher
import cn.verlu.talk.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProfileRepository {

    override suspend fun getProfile(userId: String): Profile? = withContext(ioDispatcher) {
        runCatching {
            supabase.postgrest["profiles"].select {
                filter { eq("id", userId) }
                limit(1L)
            }.decodeSingle<ProfileDto>().toDomain()
        }.getOrNull()
    }

    override suspend fun searchProfiles(query: String): List<Profile> = withContext(ioDispatcher) {
        runCatching {
            val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
            when {
                uuidRegex.matches(query) -> {
                    supabase.postgrest["profiles"].select {
                        filter { eq("id", query) }
                        limit(1L)
                    }.decodeList<ProfileDto>()
                }
                query.contains("@") -> {
                    supabase.postgrest["profiles"].select {
                        filter { ilike("email", "%$query%") }
                        limit(20L)
                    }.decodeList<ProfileDto>()
                }
                else -> {
                    supabase.postgrest["profiles"].select {
                        filter { ilike("display_name", "%$query%") }
                        order("display_name", Order.ASCENDING)
                        limit(20L)
                    }.decodeList<ProfileDto>()
                }
            }.map { it.toDomain() }
        }.getOrElse { emptyList() }
    }

    override suspend fun getCurrentUserProfile(): Profile? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return getProfile(userId)
    }
}
