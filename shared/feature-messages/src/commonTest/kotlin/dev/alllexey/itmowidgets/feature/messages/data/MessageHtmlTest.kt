package dev.alllexey.itmowidgets.feature.messages.data

import dev.alllexey.itmowidgets.feature.messages.domain.MessageBlock
import dev.alllexey.itmowidgets.feature.messages.domain.MessageSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageHtmlTest {

    @Test
    fun paragraphsBecomeBlocksWithTheirStyles() {
        val body = MessageHtml.parse("<p>Лекция <strong>переносится</strong> на <em>пятницу</em>.</p><div>Вторая <u>строка</u></div>")

        assertEquals(
            listOf(
                MessageBlock(listOf(MessageSpan("Лекция "), MessageSpan("переносится", bold = true), MessageSpan(" на "), MessageSpan("пятницу", italic = true), MessageSpan("."))),
                MessageBlock(listOf(MessageSpan("Вторая "), MessageSpan("строка", underline = true))),
            ),
            body.blocks,
        )
    }

    @Test
    fun scriptsStylesImagesAndUnsafeLinksLeaveNoTrace() {
        val body = MessageHtml.parse(
            "<style>p{color:red}</style><script>alert(1)</script><p onclick=\"x()\">Текст<img src=\"https://evil/x.png\">" +
                "<a href=\"javascript:alert(1)\">ссылка</a> <a href=\"mailto:a@b.c\">почта</a> <a href=\"/relative\">путь</a></p>",
        )

        assertEquals("Текстссылка почта путь", body.plainText)
        assertTrue(body.blocks.flatMap { it.spans }.all { it.link == null })
    }

    @Test
    fun httpsLinksAndBareAddressesAreLinks() {
        val spans = MessageHtml.parse("<p><a href=\"https://t.me/example\">чат</a>, журнал: https://example.org/labs.</p>").blocks.single().spans

        assertEquals(MessageSpan("чат", link = "https://t.me/example"), spans[0])
        assertEquals(MessageSpan("https://example.org/labs", link = "https://example.org/labs"), spans[2])
        assertEquals(".", spans.last().text)
    }

    @Test
    fun listsGetMarkersAndBreaksStayInsideABlock() {
        val body = MessageHtml.parse("Итоги:<br>первая строка<br><br><br><br>вторая<ol><li>один</li><li>два</li></ol><ul><li>пункт</li></ul>")

        assertEquals("Итоги:\nпервая строка\n\nвторая", body.blocks[0].text)
        assertEquals(listOf("1." to "один", "2." to "два", "•" to "пункт"), body.blocks.drop(1).map { it.marker to it.text })
    }

    @Test
    fun whitespaceAndEntitiesCollapseAsInABrowser() {
        val body = MessageHtml.parse("<p>  Дата:&nbsp;&nbsp;21&#46;10 &amp;\n\n  время  </p><p> </p>")

        assertEquals(listOf("Дата: 21.10 & время"), body.blocks.map { it.text })
    }

    @Test
    fun emptyOrPlainTextBodies() {
        assertTrue(MessageHtml.parse("").blocks.isEmpty())
        assertEquals("просто текст", MessageHtml.parse("просто текст").plainText)
    }

    @Test
    fun onlyAbsoluteHttpLinksAreSafe() {
        assertEquals("https://example.org/a?b=1", MessageHtml.safeLink(" https://example.org/a?b=1 "))
        assertEquals("http://example.org", MessageHtml.safeLink("HTTP://example.org".lowercase()))
        assertNull(MessageHtml.safeLink("javascript:alert(1)"))
        assertNull(MessageHtml.safeLink("https:example.org"))
        assertNull(MessageHtml.safeLink("https://exa mple.org"))
        assertNull(MessageHtml.safeLink("intent://scan#Intent;end"))
    }
}
