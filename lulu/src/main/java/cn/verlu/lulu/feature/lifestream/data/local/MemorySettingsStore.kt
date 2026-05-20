package cn.verlu.lulu.feature.lifestream.data.local

import android.content.Context
import cn.verlu.lulu.core.remote.SupabaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class MemorySettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        private const val PREFS = "memory_settings"
        private const val KEY_SHOW_CLOUD_BADGE = "show_cloud_badge"
        private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_MEDIA_CDN_BASE_URL = "media_cdn_base_url"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _showCloudBadge = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_CLOUD_BADGE, true),
    )
    private val _cloudSyncEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false),
    )
    private val _mediaCdnBaseUrl = MutableStateFlow(
        prefs.getString(KEY_MEDIA_CDN_BASE_URL, SupabaseConfig.DEFAULT_MEDIA_CDN_BASE_URL).orEmpty(),
    )

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_SHOW_CLOUD_BADGE -> _showCloudBadge.value = prefs.getBoolean(KEY_SHOW_CLOUD_BADGE, true)
            KEY_CLOUD_SYNC_ENABLED -> _cloudSyncEnabled.value = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
            KEY_MEDIA_CDN_BASE_URL -> {
                _mediaCdnBaseUrl.value =
                    prefs.getString(KEY_MEDIA_CDN_BASE_URL, SupabaseConfig.DEFAULT_MEDIA_CDN_BASE_URL).orEmpty()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    val showCloudBadge: StateFlow<Boolean> = _showCloudBadge.asStateFlow()
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()
    val mediaCdnBaseUrl: StateFlow<String> = _mediaCdnBaseUrl.asStateFlow()

    fun setShowCloudBadge(enabled: Boolean) {
        _showCloudBadge.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_CLOUD_BADGE, enabled).apply()
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        _cloudSyncEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, enabled).apply()
    }

    fun setMediaCdnBaseUrl(baseUrl: String) {
        val normalized = baseUrl.trim().trimEnd('/')
        _mediaCdnBaseUrl.value = normalized
        prefs.edit().putString(KEY_MEDIA_CDN_BASE_URL, normalized).apply()
    }

    fun isCloudSyncEnabled(): Boolean = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
    fun getMediaCdnBaseUrl(): String =
        prefs.getString(KEY_MEDIA_CDN_BASE_URL, SupabaseConfig.DEFAULT_MEDIA_CDN_BASE_URL).orEmpty()
}
