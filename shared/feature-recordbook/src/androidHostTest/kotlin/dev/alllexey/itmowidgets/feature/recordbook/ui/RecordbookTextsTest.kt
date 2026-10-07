package dev.alllexey.itmowidgets.feature.recordbook.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `formatRecordbookNumber` without `java.text` (LR-3) writes what the View's Russian `NumberFormat` wrote: HALF_EVEN on
 * the double's exact value, a comma, at most one fraction digit and thousands grouped with a no-break space.
 */
class RecordbookTextsTest {

    @Test
    fun `the table of today's outputs holds`() {
        val table = listOf(
            0.0 to "0",
            8.0 to "8",
            48.5 to "48,5",
            58.5 to "58,5",
            63.5 to "63,5",
            72.0 to "72",
            100.0 to "100",
            66.25 to "66,2",
            66.35 to "66,3",
            66.45 to "66,5",
            0.05 to "0,1",
            0.15 to "0,1",
            0.25 to "0,2",
            0.35 to "0,3",
            2.95 to "3",
            9.96 to "10",
            1000.0 to "1\u00A0000",
            1234.56 to "1\u00A0234,6",
            1234567.0 to "1\u00A0234\u00A0567",
            -3.5 to "-3,5",
        )

        table.forEach { (value, text) -> assertEquals(text, formatRecordbookNumber(value), "$value") }
    }

    @Test
    fun `every tenth and every x x5 matches NumberFormat`() {
        val reference = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru")).apply { maximumFractionDigits = 1 }
        val values = (0..20_000).flatMap { listOf(it / 100.0, it / 10.0 + 0.05, it / 7.0) } +
            listOf(1e-9, 0.049999999, 0.0500000001, 999.95, 9999.95, 123456.75, 4.5e15, 1e16)

        values.forEach { value -> assertEquals(reference.format(value), formatRecordbookNumber(value), "$value") }
    }
}
