package cn.verlu.doctor.presentation.herb

/**
 * 将接口返回的 Markdown 预览转成列表/卡片用的纯文本摘要。
 * 预览常被截断在 `![](` 中间，需先去掉「完整 + 残缺」的图片/链接语法，再取可读片段。
 */
fun markdownPreviewToPlainSummary(markdown: String, articleTitle: String = ""): String {
    if (markdown.isBlank()) return ""
    val stripped = stripMarkdownNoise(markdown)
    return pickReadableSnippet(stripped, articleTitle.trim())
}

/** 去掉 Markdown 噪声，多轮直到稳定，避免截断导致的半段 `![...](a`。 */
private fun stripMarkdownNoise(raw: String): String {
    var s = raw
    repeat(8) {
        val next = stripMarkdownNoisePass(s)
        if (next == s) return@repeat
        s = next
    }
    return s
}

private fun stripMarkdownNoisePass(input: String): String {
    var s = input

    // 图片：先完整，再「缺右括号」的截断片段（预览里极常见）
    s = Regex("!\\[[^\\]]*\\]\\([^)]*\\)").replace(s, " ")
    s = Regex("!\\[[^\\]]*\\]\\([^)]*").replace(s, " ")
    s = Regex("!\\[[^\\]]*\\]\\s*\\[[^\\]]*\\]").replace(s, " ")
    s = Regex("!\\[[^\\]]*\\]").replace(s, " ")
    s = Regex("!\\[[^\\]]*").replace(s, " ")

    s = Regex("\\S*(?:assets|static|uploads|images)/[^\\s)\\]]*").replace(s, " ")

    s = Regex("\\[([^\\]]+)\\]\\([^)]*\\)").replace(s, "$1")
    s = Regex("\\[([^\\]]+)\\]\\([^)]*").replace(s, "$1")

    s = Regex("<(https?://[^>\\s]+)>").replace(s, "$1")
    s = Regex("<[^>]+>").replace(s, " ")

    s = Regex("`+([^`]+)`+").replace(s, "$1")

    s = s.lines().map { line ->
        line.trim()
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^[-*+]\\s+"), "")
            .replace(Regex("^\\d+\\.\\s+"), "")
            .replace(Regex("^>\\s*"), "")
    }.joinToString("\n")

    s = s.replace("**", "").replace("__", "")

    s = s.replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
    s = s.replace(Regex("\\n+"), " ")
    s = Regex(" {2,}").replace(s, " ").trim()
    return s
}

/**
 * 取最适合展示的一小段：优先第一条「含足够汉字、长度像正文」的句子，避免只剩标题或碎片。
 */
private fun pickReadableSnippet(cleaned: String, articleTitle: String): String {
    if (cleaned.isBlank()) return ""

    var t = cleaned
    if (t.contains("![") || t.contains("](")) {
        t = Regex("!\\[[^\\]]*\\]?").replace(t, " ")
        t = Regex(" {2,}").replace(t, " ").trim()
    }

    val parts = t.split(Regex("[。！？.!?\\n]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .let { list ->
            if (articleTitle.isBlank()) list
            else list.filterNot { seg -> seg == articleTitle || seg == "# $articleTitle" }
        }

    fun cjkCount(p: String) = p.count { ch -> ch.code in 0x4E00..0x9FFF }

    val preferred = parts.firstOrNull { p ->
        p.length >= 12 && cjkCount(p) >= 4
    }
    val secondary = parts.firstOrNull { p ->
        p.length >= 8 && cjkCount(p) >= 2
    }
    val tertiary = parts.firstOrNull { it.length >= 6 }

    val chosen = preferred ?: secondary ?: tertiary ?: t
    val max = 280
    return if (chosen.length <= max) chosen else chosen.take(max).trimEnd() + "…"
}
