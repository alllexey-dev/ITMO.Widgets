package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvGridTest {
    @Test fun `quoted fields keep commas, line breaks and doubled quotes`() {
        val grid = CsvGrid.parse("a,\"b,c\",\"say \"\"hi\"\"\"\n\"line\nbreak\",x,y\n")

        assertEquals(listOf(listOf("a", "b,c", "say \"hi\""), listOf("line\nbreak", "x", "y")), grid.rows)
    }

    @Test fun `CRLF and LF both end a record`() {
        val grid = CsvGrid.parse("a,b\r\nc,d\ne,f")

        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")), grid.rows)
    }

    @Test fun `a BOM is not part of the first cell`() {
        assertEquals("id", CsvGrid.parse("\uFEFFid,name\n").cell(0, 0))
    }

    @Test fun `empty cells stay and ragged rows are padded`() {
        val grid = CsvGrid.parse("a,,c\nd\n,,\n")

        assertEquals(3, grid.width)
        assertEquals(listOf(listOf("a", "", "c"), listOf("d", "", ""), listOf("", "", "")), grid.rows)
        assertEquals("", grid.cell(1, 2))
        assertEquals("", grid.cell(9, 9))
    }

    @Test fun `empty text is an empty grid`() {
        val grid = CsvGrid.parse("")

        assertEquals(0, grid.height)
        assertEquals(0, grid.width)
    }

    @Test fun `the multi-header fixture keeps its shape and the own total`() {
        val grid = SheetFixtures.csv("grades_multiheader.csv")

        assertEquals(8, grid.height)
        assertEquals(13, grid.width)
        assertEquals("123456", grid.cell(5, 0))
        assertEquals("66,3", grid.cell(5, 11))
        assertEquals("5A", grid.cell(5, 12))
        assertEquals("7\nдоп", grid.cell(6, 6))
    }

    @Test fun `column names follow the spreadsheet letters`() {
        assertEquals("A", columnName(0))
        assertEquals("Z", columnName(25))
        assertEquals("AA", columnName(26))
        assertEquals("AZ", columnName(51))
    }
}
