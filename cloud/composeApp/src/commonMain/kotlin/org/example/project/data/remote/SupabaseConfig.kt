package cn.verlu.cloud.data.remote

object SupabaseConfig {
    const val URL = "https://jlzfvxxwzcpvtzdemcpm.supabase.co"
    const val ANON_KEY = "sb_publishable_MCPNEzhVvka_MagKEBD6zQ__dgpWoW0"

    /** 需在 Supabase 控制台 Storage 中创建同名 bucket，并配置 RLS/策略（见 docs/storage-architecture.md）。 */
    const val STORAGE_BUCKET = "cloud-user-files"

    const val REDIRECT_URI = "verlucloud://login"
    const val DESKTOP_QR_TICKET_PREFIX = "verlu://cloud-login?ticket="

    /**
     * 若填写为 **HTTPS** 落地页（需自行部署并完成与桌面对账），扫码内容将为 `BASE?ticket=xxx`，手机浏览器可直接打开。
     * 留空则使用 [DESKTOP_QR_TICKET_PREFIX] 自定义 scheme（需配套手机 App 或后续深度链接）。
     */
    const val DESKTOP_QR_PUBLIC_PAGE: String = ""

    fun buildDesktopQrLink(ticketHex: String): String {
        val base = DESKTOP_QR_PUBLIC_PAGE.trim()
        return if (base.isNotEmpty()) {
            val sep = if ('?' in base) "&" else "?"
            "$base${sep}ticket=$ticketHex"
        } else {
            DESKTOP_QR_TICKET_PREFIX + ticketHex
        }
    }
}
