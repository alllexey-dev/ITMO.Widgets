package dev.alllexey.itmowidgets.core.text

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Instant

/**
 * [DateTexts] against java.time + Locale "ru" for every day 2025-09-01..2027-08-31 at 00:00, 09:30 and 23:59 in
 * Europe/Moscow (SP-07 harness). The kotlinx side starts from a `kotlin.time.Instant`, as the app starts from an
 * instant or an `OffsetDateTime` moved to the academic zone.
 */
class DateTextsParityTest {

    private class Sample(val zoned: ZonedDateTime, val instant: Instant, val local: LocalDateTime)

    /** [foldSpaces]: `ofLocalized*` writes U+202F before "г." from CLDR 42 (JDK 21) on, a plain space before. */
    private class Row(
        val id: String,
        val java: (Sample) -> String,
        val common: (Sample) -> String,
        val foldSpaces: Boolean = false,
    )

    private val ru = Locale.forLanguageTag("ru")
    private val moscowJava = ZoneId.of("Europe/Moscow")
    private val moscow = TimeZone.of("Europe/Moscow")

    private val samples: List<Sample> = buildList {
        var day = LocalDate.of(2025, 9, 1)
        while (!day.isAfter(LocalDate.of(2027, 8, 31))) {
            for (time in listOf(LocalTime.of(0, 0), LocalTime.of(9, 30), LocalTime.of(23, 59))) {
                val zoned = ZonedDateTime.of(day, time, moscowJava)
                val instant = Instant.fromEpochSeconds(zoned.toEpochSecond(), zoned.nano)
                add(Sample(zoned, instant, instant.toLocalDateTime(moscow)))
            }
            day = day.plusDays(1)
        }
    }

    private fun pattern(id: String, common: (Sample) -> String, locale: Locale = ru) =
        Row(id, { DateTimeFormatter.ofPattern(id, locale).format(it.zoned) }, common)

    private fun localized(id: String, formatter: DateTimeFormatter, common: (Sample) -> String) =
        Row(id, { formatter.withLocale(ru).format(it.zoned) }, common, foldSpaces = true)

    private val rows: List<Row> = listOf(
        pattern("HH:mm", { it.local.time.format(DateTexts.TIME) }, Locale.ROOT),
        pattern("HH:mm", { it.local.time.format(DateTexts.TIME) }),
        pattern("d", { it.local.date.format(DateTexts.DAY) }),
        pattern("MMM", { it.local.date.format(DateTexts.MONTH_SHORT) }),
        pattern("EEEE", { it.local.date.format(DateTexts.WEEKDAY) }),
        pattern("d MMMM", { it.local.date.format(DateTexts.DAY_MONTH) }),
        pattern("d MMMM yyyy", { it.local.date.format(DateTexts.DAY_MONTH_YEAR) }),
        pattern("EEEE, d MMMM", { it.local.date.format(DateTexts.WEEKDAY_DAY_MONTH) }),
        pattern("EEEE, d MMMM yyyy", { it.local.date.format(DateTexts.WEEKDAY_DAY_MONTH_YEAR) }),
        pattern("EEE, d MMMM", { it.local.date.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH) }),
        pattern("EEE, d MMM", { it.local.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH) }),
        pattern("EE, d MMMM, HH:mm", { it.local.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH_TIME) }),
        pattern("d MMM, HH:mm", { it.local.format(DateTexts.DAY_SHORT_MONTH_TIME) }),
        Row(
            "LLLL yyyy",
            { DateTimeFormatter.ofPattern("LLLL yyyy", ru).format(java.time.YearMonth.from(it.zoned)) },
            { YearMonth(it.local.year, it.local.month).format(DateTexts.MONTH_YEAR) }
        ),
        Row(
            "yyyy-MM-dd HH:mm:ss",
            { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", ru).withZone(moscowJava).format(it.zoned.toInstant()) },
            { DateTexts.diagnostics(it.instant, moscow) }
        ),
        Row(
            "yyyyMMdd'T'HHmmss'Z'",
            { DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(it.zoned.toInstant()) },
            { DateTexts.icsUtc(it.instant) }
        ),
        Row(
            "DayOfWeek FULL",
            { it.zoned.dayOfWeek.getDisplayName(TextStyle.FULL, ru) },
            { DateTexts.weekday(it.local.dayOfWeek) }
        ),
        Row(
            "DayOfWeek SHORT",
            { it.zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, ru) },
            { DateTexts.shortWeekday(it.local.dayOfWeek) }
        ),
        Row(
            "Month FULL_STANDALONE",
            { it.zoned.month.getDisplayName(TextStyle.FULL_STANDALONE, ru) },
            { DateTexts.standaloneMonth(it.local.month) }
        ),
        localized("localized FULL date", DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) {
            it.local.date.format(DateTexts.LOCALIZED_FULL_DATE)
        },
        localized("localized LONG date", DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) {
            it.local.date.format(DateTexts.LOCALIZED_LONG_DATE)
        },
        localized("localized MEDIUM date-time", DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) {
            it.local.format(DateTexts.LOCALIZED_MEDIUM_DATE_TIME)
        },
        Row(
            "parse OffsetDateTime.toString",
            { it.zoned.toInstant().toString() },
            { DateTexts.parseOffsetInstant(it.zoned.toOffsetDateTime().toString()).toString() }
        ),
        Row(
            "parse ISO_OFFSET_DATE_TIME",
            { it.zoned.toInstant().toString() },
            { DateTexts.parseOffsetInstant(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(it.zoned)).toString() }
        ),
    )

    private fun String.folded(): String = replace('\u202F', ' ').replace('\u00A0', ' ')

    @Test
    fun `every pattern matches java time for two academic years`() {
        assertEquals(730 * 3, samples.size)
        val differences = rows.flatMap { row ->
            samples.mapNotNull { sample ->
                val expected = row.java(sample)
                val actual = row.common(sample)
                val differs = if (row.foldSpaces) expected.folded() != actual.folded() else expected != actual
                if (differs) "${row.id} at ${sample.zoned}: java \"$expected\", DateTexts \"$actual\"" else null
            }
        }
        assertEquals(differences.take(10).joinToString("\n"), 0, differences.size)
    }

    @Test
    fun `names cover every month and weekday`() {
        for (month in Month.entries) {
            val date = LocalDate.of(2026, month, 1)
            assertEquals(DateTimeFormatter.ofPattern("MMMM", ru).format(date), DateTexts.Names.MONTHS_GENITIVE.names[month.ordinal])
            assertEquals(DateTimeFormatter.ofPattern("LLLL", ru).format(date), DateTexts.Names.MONTHS_NOMINATIVE.names[month.ordinal])
            assertEquals(DateTimeFormatter.ofPattern("MMM", ru).format(date), DateTexts.Names.MONTHS_SHORT_GENITIVE.names[month.ordinal])
        }
        for (day in DayOfWeek.entries) {
            assertEquals(day.getDisplayName(TextStyle.FULL, ru), DateTexts.Names.DAYS_FULL.names[day.ordinal])
            assertEquals(day.getDisplayName(TextStyle.SHORT, ru), DateTexts.Names.DAYS_SHORT.names[day.ordinal])
        }
    }

    @Test
    fun `offset text without seconds parses to the same instant`() {
        val text = OffsetDateTime.of(2026, 9, 1, 9, 30, 0, 0, ZoneOffset.ofHours(3)).toString()

        assertEquals("2026-09-01T09:30+03:00", text)
        assertEquals(Instant.parse("2026-09-01T06:30:00Z"), DateTexts.parseOffsetInstant(text))
    }
}
