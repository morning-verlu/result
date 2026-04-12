package cn.verlu.doctor.presentation.herb.ui

/**
 * 将「挤在同一行」的多张 `![alt](url)` 拆成独立段落（段落间空行）。
 *
 * mikepenz 对**块级图**走 Coil ImageTransformer；**行内图**嵌在 Text 行盒里，行高受限，易裁切。
 * 条文里常见 `…jpg)![下一张` 连写（中间可有零宽字符），必须先拆开。
 */
fun normalizeMarkdownBlockImages(markdown: String): String {
    if (markdown.isEmpty()) return markdown
    return markdown.replace(
        Regex("\\)(?:\\s|\\u200B|\\u200C|\\u200D|\\uFEFF)*(?=\\!\\[)"),
        ")\n\n",
    )
}

/**
 * 从 Markdown 正文中收集图片 URL（顺序与文中出现顺序一致，去重保序）。
 */
fun extractMarkdownImageUrls(markdown: String): List<String> {
    val seen = LinkedHashSet<String>()
    // ![](url) 或 ![alt](url "title")
    Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
        .findAll(markdown)
        .forEach { m ->
            val raw = m.groupValues[1].trim()
            val url = raw.split(Regex("\\s+")).firstOrNull()?.trim()?.trim('"') ?: return@forEach
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") || url.startsWith("file://")) {
                seen.add(url)
            }
        }
    // <img src="...">
    Regex("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        .findAll(markdown)
        .forEach { m ->
            val url = m.groupValues[1].trim()
            if (url.isNotBlank()) seen.add(url)
        }
    return seen.toList()
}
