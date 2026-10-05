package dev.alllexey.itmowidgets.core.text

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.alternativeParsing
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.datetime.parse
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Russian date and time texts, equal character for character to `DateTimeFormatter.ofPattern(pattern,
 * Locale.forLanguageTag("ru"))` for every pattern the app uses (`DateTextsParityTest`). Each format names its
 * java.time pattern; a port replaces `ofPattern(...)` with the format of the same pattern (recipe
 * kotlinx-time-migration).
 */
object DateTexts {

    /** Month and weekday names as java.time (CLDR, Locale "ru") renders them. */
    object Names {
        /** `MMMM`: the format (genitive) form, "3 октября". */
        val MONTHS_GENITIVE = MonthNames(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )

        /** `LLLL` and `TextStyle.FULL_STANDALONE`: the stand-alone (nominative) form, "октябрь 2026". */
        val MONTHS_NOMINATIVE = MonthNames(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
        )

        /** `MMM`: the abbreviated format form, with dots except May, "сент.", "мая". */
        val MONTHS_SHORT_GENITIVE = MonthNames(
            "янв.", "февр.", "мар.", "апр.", "мая", "июн.",
            "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.",
        )

        /** `EEEE` and `TextStyle.FULL`, Monday first. */
        val DAYS_FULL = DayOfWeekNames(
            "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье",
        )

        /** `E`, `EE`, `EEE` and `TextStyle.SHORT`, Monday first. */
        val DAYS_SHORT = DayOfWeekNames("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    }

    /** `HH:mm`, "09:30". */
    val TIME: DateTimeFormat<LocalTime> = LocalTime.Format { hour(); char(':'); minute() }

    /** `d`, "1". */
    val DAY: DateTimeFormat<LocalDate> = LocalDate.Format { day(Padding.NONE) }

    /** `MMM` alone: still the genitive form in java.time, "сент.", "мая". */
    val MONTH_SHORT: DateTimeFormat<LocalDate> = LocalDate.Format { monthName(Names.MONTHS_SHORT_GENITIVE) }

    /** `EEEE`, "вторник". */
    val WEEKDAY: DateTimeFormat<LocalDate> = LocalDate.Format { dayOfWeek(Names.DAYS_FULL) }

    /** `d MMMM`, "1 сентября". */
    val DAY_MONTH: DateTimeFormat<LocalDate> = LocalDate.Format {
        day(Padding.NONE); char(' '); monthName(Names.MONTHS_GENITIVE)
    }

    /** `d MMMM yyyy`, "1 сентября 2026". */
    val DAY_MONTH_YEAR: DateTimeFormat<LocalDate> = LocalDate.Format { date(DAY_MONTH); char(' '); year() }

    /** `EEEE, d MMMM`, "вторник, 1 сентября". */
    val WEEKDAY_DAY_MONTH: DateTimeFormat<LocalDate> = LocalDate.Format {
        dayOfWeek(Names.DAYS_FULL); chars(", "); date(DAY_MONTH)
    }

    /** `EEEE, d MMMM yyyy`, "вторник, 1 сентября 2026". */
    val WEEKDAY_DAY_MONTH_YEAR: DateTimeFormat<LocalDate> = LocalDate.Format {
        dayOfWeek(Names.DAYS_FULL); chars(", "); date(DAY_MONTH_YEAR)
    }

    /** `EEE, d MMMM`, "вт, 1 сентября". */
    val SHORT_WEEKDAY_DAY_MONTH: DateTimeFormat<LocalDate> = LocalDate.Format {
        dayOfWeek(Names.DAYS_SHORT); chars(", "); date(DAY_MONTH)
    }

    /** `EEE, d MMM`, "вт, 1 сент.". */
    val SHORT_WEEKDAY_DAY_SHORT_MONTH: DateTimeFormat<LocalDate> = LocalDate.Format {
        dayOfWeek(Names.DAYS_SHORT); chars(", ")
        day(Padding.NONE); char(' '); monthName(Names.MONTHS_SHORT_GENITIVE)
    }

    /** `EE, d MMMM, HH:mm`, "вт, 1 сентября, 09:30". */
    val SHORT_WEEKDAY_DAY_MONTH_TIME: DateTimeFormat<LocalDateTime> = LocalDateTime.Format {
        date(SHORT_WEEKDAY_DAY_MONTH); chars(", "); time(TIME)
    }

    /** `d MMM, HH:mm`, "1 сент., 09:30". */
    val DAY_SHORT_MONTH_TIME: DateTimeFormat<LocalDateTime> = LocalDateTime.Format {
        day(Padding.NONE); char(' '); monthName(Names.MONTHS_SHORT_GENITIVE); chars(", "); time(TIME)
    }

    /** `LLLL yyyy` on a year and month, "сентябрь 2026". */
    val MONTH_YEAR: DateTimeFormat<YearMonth> = YearMonth.Format {
        monthName(Names.MONTHS_NOMINATIVE); char(' '); year()
    }

    /** `yyyy-MM-dd HH:mm:ss`, "2026-09-01 09:30:00"; see [diagnostics]. */
    val DIAGNOSTICS: DateTimeFormat<LocalDateTime> = LocalDateTime.Format {
        year(); char('-'); monthNumber(); char('-'); day()
        char(' '); hour(); char(':'); minute(); char(':'); second()
    }

    /** `yyyyMMdd'T'HHmmss'Z'` in UTC, "20260901T063000Z"; see [icsUtc]. */
    val ICS_UTC: DateTimeFormat<DateTimeComponents> = DateTimeComponents.Format {
        year(); monthNumber(); day(); char('T'); hour(); minute(); second(); char('Z')
    }

    // ofLocalized* under Locale "ru". java.time puts U+202F before "г." from CLDR 42 on and a plain space before it;
    // these pin "ru" with a plain space whatever the device locale (SP-07 finding 2).

    /** `ofLocalizedDate(FormatStyle.FULL)`: "вторник, 1 сентября 2026 г.". */
    val LOCALIZED_FULL_DATE: DateTimeFormat<LocalDate> = LocalDate.Format {
        date(WEEKDAY_DAY_MONTH_YEAR); chars(" г.")
    }

    /** `ofLocalizedDate(FormatStyle.LONG)`: "1 сентября 2026 г.". */
    val LOCALIZED_LONG_DATE: DateTimeFormat<LocalDate> = LocalDate.Format { date(DAY_MONTH_YEAR); chars(" г.") }

    /** `ofLocalizedDateTime(FormatStyle.MEDIUM)`: "1 сент. 2026 г., 09:30:00". */
    val LOCALIZED_MEDIUM_DATE_TIME: DateTimeFormat<LocalDateTime> = LocalDateTime.Format {
        day(Padding.NONE); char(' '); monthName(Names.MONTHS_SHORT_GENITIVE); char(' '); year(); chars(" г., ")
        hour(); char(':'); minute(); char(':'); second()
    }

    /**
     * An ISO offset date-time whose seconds may be missing: `OffsetDateTime.toString()` drops `:00` seconds
     * ("2026-09-01T09:30+03:00"), which `Instant.parse` rejects.
     */
    val ISO_OFFSET_LENIENT_SECONDS: DateTimeFormat<DateTimeComponents> = DateTimeComponents.Format {
        date(LocalDate.Formats.ISO)
        alternativeParsing({ char('t') }) { char('T') }
        hour(); char(':'); minute()
        optional { char(':'); second(); optional { char('.'); secondFraction(1, 9) } }
        offset(UtcOffset.Formats.ISO)
    }

    /** `DayOfWeek.getDisplayName(TextStyle.FULL, ru)` of [day], "вторник". */
    fun weekday(day: DayOfWeek): String = Names.DAYS_FULL.names[day.ordinal]

    /** `DayOfWeek.getDisplayName(TextStyle.SHORT, ru)` of [day], "вт". */
    fun shortWeekday(day: DayOfWeek): String = Names.DAYS_SHORT.names[day.ordinal]

    /** `Month.getDisplayName(TextStyle.FULL_STANDALONE, ru)` of [month], "сентябрь". */
    fun standaloneMonth(month: Month): String = Names.MONTHS_NOMINATIVE.names[month.ordinal]

    /** [instant] in [zone] as `ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)` writes it. */
    fun diagnostics(instant: Instant, zone: TimeZone): String = instant.toLocalDateTime(zone).format(DIAGNOSTICS)

    /** [instant] as `ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(UTC)` writes it. */
    fun icsUtc(instant: Instant): String = instant.format(ICS_UTC, UtcOffset.ZERO)

    /** `OffsetDateTime.parse(text).toInstant()`, also for text without seconds; see [ISO_OFFSET_LENIENT_SECONDS]. */
    fun parseOffsetInstant(text: String): Instant = Instant.parse(text, ISO_OFFSET_LENIENT_SECONDS)
}
