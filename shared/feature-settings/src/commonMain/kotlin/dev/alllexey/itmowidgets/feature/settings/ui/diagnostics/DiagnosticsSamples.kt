package dev.alllexey.itmowidgets.feature.settings.ui.diagnostics

import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.core.text.DateTexts
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Synthetic journal of the previews and host tests: a crash, an error and a warning, newest first. */
internal object DiagnosticsSamples {

    private val zone = TimeZone.of("Europe/Moscow")

    val entries = listOf(
        DiagnosticEntry(
            Instant.parse("2026-10-07T08:41:05Z"), DiagnosticLevel.CRASH, "Crash",
            "IllegalStateException: Fragment SettingsFragment not attached to a context.",
            "java.lang.IllegalStateException: Fragment SettingsFragment not attached to a context.\n" +
                "\tat androidx.fragment.app.Fragment.requireContext(Fragment.java:1027)",
        ),
        DiagnosticEntry(
            Instant.parse("2026-10-07T06:30:12Z"), DiagnosticLevel.ERROR, "Schedule",
            "Не удалось обновить расписание", "java.io.IOException: timeout",
        ),
        DiagnosticEntry(
            Instant.parse("2026-10-06T21:02:47Z"), DiagnosticLevel.WARNING, "Widgets",
            "Виджет расписания обновлён из кэша", null,
        ),
    )

    fun timeLabel(entry: DiagnosticEntry): String = DateTexts.diagnostics(entry.at, zone)
}
