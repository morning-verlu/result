package cn.verlu.cloud

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** Desktop（JVM）目标为 true，Android 为 false。用于展示扫码登录等仅桌面需要的 UI。 */
expect fun isDesktopPlatform(): Boolean