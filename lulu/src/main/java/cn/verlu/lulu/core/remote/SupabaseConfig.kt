package cn.verlu.lulu.core.remote

import androidx.core.net.toUri

object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "sb_publishable_MCPNEzhVvka_MagKEBD6zQ__dgpWoW0"
    const val STORAGE_BUCKET = "cloud-user-files"
    const val DEFAULT_MEDIA_CDN_BASE_URL = "https://img.jkot.net"

    fun mapMediaUrl(url: String, cdnBaseUrl: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return url
        if (cdnBaseUrl.isBlank()) return url
        val source = runCatching { url.toUri() }.getOrNull() ?: return url
        val cdn = runCatching { cdnBaseUrl.toUri() }.getOrNull() ?: return url
        val cdnHost = cdn.host ?: return url
        val cdnScheme = cdn.scheme ?: "https"
        return source.buildUpon()
            .scheme(cdnScheme)
            .authority(cdnHost)
            .build()
            .toString()
    }
}
