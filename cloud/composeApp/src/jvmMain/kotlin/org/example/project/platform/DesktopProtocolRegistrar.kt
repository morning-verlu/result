package cn.verlu.cloud.platform

import java.io.File

private const val SCHEME = "verlucloud"
private const val MAIN_CLASS = "cn.verlu.cloud.MainKt"

/**
 * 注册 verlucloud:// 协议到当前用户（Windows HKCU）�?
 * 仅在 Desktop 端调用，失败时静默跳过，避免影响主流程�?
 */
fun ensureDesktopProtocolRegistered() {
    if (!isWindows()) return
    runCatching {
        val launchCommand = currentLaunchCommand() ?: return
        registerWindowsScheme(launchCommand)
    }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)

private fun currentLaunchCommand(): String? {
    val command = ProcessHandle.current().info().command().orElse(null) ?: return null
    if (!File(command).exists()) return null

    // ???? exe / app????? "%1" ??????
    val lower = command.lowercase()
    val isJvmRuntime = lower.endsWith("java.exe") || lower.endsWith("javaw.exe") || lower.endsWith("java")
    if (!isJvmRuntime) return "\"$command\" \"%1\""

    // ????gradle run???? java ?? MainKt????? -cp + ?????? deep link
    val classpath = System.getProperty("java.class.path").orEmpty().trim()
    if (classpath.isEmpty()) return null
    return "\"$command\" -cp \"$classpath\" $MAIN_CLASS \"%1\""
}

private fun registerWindowsScheme(launchCommand: String) {
    val baseKey = "HKCU\\Software\\Classes\\$SCHEME"
    runReg("add", baseKey, "/ve", "/t", "REG_SZ", "/d", "URL:VERLU Cloud Protocol", "/f")
    runReg("add", baseKey, "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f")
    runReg(
        "add",
        "$baseKey\\shell\\open\\command",
        "/ve",
        "/t",
        "REG_SZ",
        "/d",
        launchCommand,
        "/f",
    )
}

private fun runReg(vararg args: String) {
    val full = listOf("reg") + args
    ProcessBuilder(full)
        .redirectErrorStream(true)
        .start()
        .waitFor()
}
