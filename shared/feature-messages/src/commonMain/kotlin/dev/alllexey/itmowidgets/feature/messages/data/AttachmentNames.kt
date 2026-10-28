package dev.alllexey.itmowidgets.feature.messages.data

/**
 * A file name ISU gave, safe as one path segment: no separators, control characters or leading dots, at most 120
 * characters with the extension kept.
 */
internal object AttachmentNames {

    fun safe(name: String): String {
        val cleaned = name.map { if (it in FORBIDDEN || it.code < 0x20) '_' else it }.joinToString("").trim().trimStart('.')
        if (cleaned.isEmpty()) return FALLBACK
        if (cleaned.length <= MAX_LENGTH) return cleaned
        val extension = cleaned.substringAfterLast('.', "").takeIf { it.length in 1..10 }?.let { ".$it" }.orEmpty()
        return cleaned.take(MAX_LENGTH - extension.length) + extension
    }

    private const val MAX_LENGTH = 120
    private const val FALLBACK = "file"
    private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
}
