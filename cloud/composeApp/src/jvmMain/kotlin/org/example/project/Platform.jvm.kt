package cn.verlu.cloud

class JVMPlatform: Platform {
    override val name: String = "Desktop JVM ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun isDesktopPlatform(): Boolean = true