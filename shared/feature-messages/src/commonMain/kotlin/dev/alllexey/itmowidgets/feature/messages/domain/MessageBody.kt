package dev.alllexey.itmowidgets.feature.messages.domain

/**
 * A message body reduced to what the app draws: paragraphs of styled text runs and links. It is built from ISU's HTML
 * by a whitelist, so no markup, script, style or image of the original reaches a screen.
 */
data class MessageBody(val blocks: List<MessageBlock>) {

    /** The text without styles, blocks on their own lines: the list's preview and accessibility. */
    val plainText: String get() = blocks.joinToString("\n") { block -> (block.marker?.let { "$it " } ?: "") + block.text }

    companion object {
        val EMPTY = MessageBody(emptyList())
    }
}

/** A paragraph or a list item; [marker] is `•` or `1.` for an item. Lines inside a block are split by `\n`. */
data class MessageBlock(val spans: List<MessageSpan>, val marker: String? = null) {
    val text: String get() = spans.joinToString("") { it.text }
}

/** A run of text with one style; [link] is an `http` or `https` address, never any other scheme. */
data class MessageSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val link: String? = null,
)
