package dev.alllexey.itmowidgets.core.text

/**
 * HTML from ITMO reduced to what the app draws: paragraphs of styled text runs and links. [SafeHtml] builds it by a
 * whitelist, so no markup, script, style or image of the original reaches a screen.
 */
data class RichText(val blocks: List<RichTextBlock>) {

    /** The text without styles, blocks on their own lines: a list's preview and accessibility. */
    val plainText: String get() = blocks.joinToString("\n") { block -> (block.marker?.let { "$it " } ?: "") + block.text }

    companion object {
        val EMPTY = RichText(emptyList())
    }
}

/** A paragraph or a list item; [marker] is `•` or `1.` for an item. Lines inside a block are split by `\n`. */
data class RichTextBlock(val spans: List<RichTextSpan>, val marker: String? = null) {
    val text: String get() = spans.joinToString("") { it.text }
}

/** A run of text with one style; [link] is an `http` or `https` address, never any other scheme. */
data class RichTextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val link: String? = null,
)
