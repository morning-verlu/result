package cn.verlu.lulu.presentation.update

internal fun isInRollout(
    installId: String,
    packageName: String,
    rolloutPercent: Int,
): Boolean {
    val percent = rolloutPercent.coerceIn(0, 100)
    if (percent == 0) return false
    if (percent == 100) return true
    return rolloutBucket(installId, packageName) <= percent
}

internal fun rolloutBucket(installId: String, packageName: String): Int =
    ((installId.plus(packageName).hashCode() and Int.MAX_VALUE) % 100) + 1
