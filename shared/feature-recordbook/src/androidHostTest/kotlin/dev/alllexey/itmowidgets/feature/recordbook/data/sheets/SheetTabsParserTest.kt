package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFixtures
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetTabsParserTest {
    @Test fun `every tab of the html view is read with its gid`() {
        assertEquals(
            listOf(SheetTab(0, "Шаблон"), SheetTab(11, "All"), SheetTab(22, "P3110"), SheetTab(33, "BARS (Fall semester 2026)")),
            SheetTabsParser.parse(SheetFixtures.text("tabs_htmlview.html")),
        )
    }

    @Test fun `JavaScript escapes in names are decoded`() {
        val html = """items.push({name: "ИТОГ \/ a\x3db \"q\" \\ x\ny", pageUrl: "https:\/\/x", gid: "5",initialSheet: false});"""

        assertEquals(listOf(SheetTab(5, "ИТОГ / a=b \"q\" \\ x\ny")), SheetTabsParser.parse(html))
    }

    @Test fun `a repeated gid is one tab`() {
        val item = """items.push({name: "P3110", pageUrl: "u", gid: "22"});"""
        val other = """items.push({name: "Копия", pageUrl: "u", gid: "22"});"""

        assertEquals(listOf(SheetTab(22, "P3110")), SheetTabsParser.parse(item + "\n" + other))
    }

    @Test fun `a page without tabs gives none`() {
        assertEquals(emptyList<SheetTab>(), SheetTabsParser.parse(SheetFixtures.text("login.html")))
        assertEquals(emptyList<SheetTab>(), SheetTabsParser.parse("""items.push({name: "x", pageUrl: "u", gid: "abc"});"""))
    }
}
