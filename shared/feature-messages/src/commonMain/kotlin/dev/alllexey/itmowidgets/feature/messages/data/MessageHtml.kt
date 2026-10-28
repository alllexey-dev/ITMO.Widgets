package dev.alllexey.itmowidgets.feature.messages.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBlock
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBody
import dev.alllexey.itmowidgets.feature.messages.domain.MessageSpan

/**
 * Turns the HTML of an ISU message (TinyMCE output: `p`, `div`, `span`, `br`, `strong`, `em`, `u`, `a`, `ol`/`ul`,
 * tables) into a [MessageBody] by a whitelist. Text, bold, italic, underline, lists and `http(s)` links survive;
 * everything else (styles, classes, images, scripts, forms, other link schemes) is dropped, keeping the text it
 * holds. Bare `http(s)` addresses in the text become links too, as teachers often paste them unformatted.
 */
internal object MessageHtml {

    fun parse(html: String): MessageBody {
        if (html.isBlank()) return MessageBody.EMPTY
        val builder = Builder()
        builder.walk(Ksoup.parseBodyFragment(html).body(), Style())
        return MessageBody(builder.finish())
    }

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val link: String? = null,
    )

    private class Builder {
        private val blocks = mutableListOf<MessageBlock>()
        private var spans = mutableListOf<MessageSpan>()
        private var marker: String? = null

        fun walk(node: Node, style: Style) {
            when (node) {
                is TextNode -> text(node.getWholeText(), style)
                is Element -> element(node, style)
            }
        }

        private fun element(element: Element, style: Style) {
            val tag = element.normalName()
            if (tag in SKIPPED) return
            when (tag) {
                "br" -> return text("\n", style, keepBreak = true)
                "ul", "ol" -> return list(element, style, ordered = tag == "ol")
                "td", "th" -> {
                    children(element, style)
                    return text(" ", style)
                }
            }
            val block = tag in BLOCKS
            if (block) flush()
            children(element, styled(element, tag, style))
            if (block) flush()
        }

        private fun list(element: Element, style: Style, ordered: Boolean) {
            flush()
            var number = 0
            element.children().forEach { item ->
                if (item.normalName() != "li") return@forEach walk(item, style)
                number++
                marker = if (ordered) "$number." else BULLET
                children(item, style)
                flush()
            }
        }

        private fun children(element: Element, style: Style) = element.childNodes().forEach { walk(it, style) }

        private fun styled(element: Element, tag: String, style: Style): Style = when (tag) {
            "b", "strong" -> style.copy(bold = true)
            "i", "em" -> style.copy(italic = true)
            "u" -> style.copy(underline = true)
            "h1", "h2", "h3", "h4", "h5", "h6" -> style.copy(bold = true)
            "a" -> style.copy(link = safeLink(element.attr("href")) ?: style.link)
            else -> style
        }

        /** Whitespace collapses as a browser would; [keepBreak] keeps a `br`. */
        private fun text(raw: String, style: Style, keepBreak: Boolean = false) {
            val text = if (keepBreak) raw else raw.replace(WHITESPACE, " ")
            if (text.isEmpty()) return
            val last = spans.lastOrNull()
            // No space at the start of a line, and never two in a row.
            val trimmed = if (text.startsWith(" ") && (last == null || last.text.endsWith(" ") || last.text.endsWith("\n"))) {
                text.trimStart(' ')
            } else {
                text
            }
            if (trimmed.isEmpty()) return
            val run = MessageSpan(trimmed, style.bold, style.italic, style.underline, style.link)
            if (last != null && last.copy(text = "") == run.copy(text = "")) {
                spans[spans.lastIndex] = last.copy(text = last.text + trimmed)
            } else {
                spans += run
            }
        }

        fun flush() {
            val cleaned = trim(spans)
            if (cleaned.isNotEmpty()) blocks += MessageBlock(cleaned.flatMap(::autolink), marker)
            spans = mutableListOf()
            marker = null
        }

        fun finish(): List<MessageBlock> {
            flush()
            return blocks
        }

        /** Drops spaces and line breaks at the block's ends and runs left empty. */
        private fun trim(runs: List<MessageSpan>): List<MessageSpan> {
            val list = runs.toMutableList()
            while (list.isNotEmpty()) {
                val first = list.first().let { it.copy(text = it.text.trimStart(' ', '\n')) }
                if (first.text.isEmpty()) list.removeAt(0) else { list[0] = first; break }
            }
            while (list.isNotEmpty()) {
                val last = list.last().let { it.copy(text = it.text.trimEnd(' ', '\n')) }
                if (last.text.isEmpty()) list.removeAt(list.lastIndex) else { list[list.lastIndex] = last; break }
            }
            return list.map { it.copy(text = it.text.replace(SPACE_AROUND_BREAK, "\n").replace(BREAKS, "\n\n")) }
        }

        /** Splits bare `http(s)` addresses out of an unlinked run. */
        private fun autolink(span: MessageSpan): List<MessageSpan> {
            if (span.link != null) return listOf(span)
            val matches = BARE_URL.findAll(span.text).toList()
            if (matches.isEmpty()) return listOf(span)
            val parts = mutableListOf<MessageSpan>()
            var at = 0
            matches.forEach { match ->
                val url = match.value.trimEnd(*TRAILING_PUNCTUATION)
                if (match.range.first > at) parts += span.copy(text = span.text.substring(at, match.range.first))
                parts += span.copy(text = url, link = url)
                at = match.range.first + url.length
            }
            if (at < span.text.length) parts += span.copy(text = span.text.substring(at))
            return parts
        }
    }

    /** Only absolute `http` and `https` addresses; `javascript:`, `mailto:`, relative links and the like are text. */
    internal fun safeLink(href: String): String? {
        val value = href.trim()
        val scheme = value.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (!value.substringAfter(':').startsWith("//") || value.any { it.isWhitespace() || it.code < 0x20 }) return null
        return value
    }

    private const val BULLET = "•"
    private val SKIPPED = setOf("script", "style", "head", "title", "img", "iframe", "object", "embed", "svg", "form", "input", "button", "select", "textarea", "noscript", "template")
    private val BLOCKS = setOf("p", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "table", "blockquote", "pre", "section", "article", "header", "footer", "hr", "dl", "dt", "dd", "center")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val SPACE_AROUND_BREAK = Regex(" *\n *")
    private val BREAKS = Regex("\n{3,}")
    private val BARE_URL = Regex("https?://[^\\s<>\"']+")
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', '»')
}
