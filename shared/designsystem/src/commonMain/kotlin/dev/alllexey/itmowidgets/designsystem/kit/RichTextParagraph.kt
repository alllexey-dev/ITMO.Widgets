package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.RichTextBlock
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * One block of ITMO's HTML as `SafeHtml` let it through: a paragraph, or a list item with its marker hanging before
 * the text. Links are `http(s)` addresses; a tap goes to [onLink].
 */
@Composable
fun RichTextParagraph(block: RichTextBlock, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ItmoTheme.colorScheme
    val links = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
    val text: AnnotatedString = buildAnnotatedString {
        block.spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
            )
            val link = span.link
            if (link == null) {
                withStyle(style) { append(span.text) }
            } else {
                withLink(LinkAnnotation.Url(link, links) { onLink(link) }) { withStyle(style) { append(span.text) } }
            }
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        block.marker?.let { Text(it, Modifier.widthIn(min = MarkerWidth), style = ItmoTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
        Text(text, style = ItmoTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

private val MarkerWidth = 20.dp
