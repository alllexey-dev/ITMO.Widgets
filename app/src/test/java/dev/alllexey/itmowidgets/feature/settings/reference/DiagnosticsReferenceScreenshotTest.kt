package dev.alllexey.itmowidgets.feature.settings.reference

import android.view.View
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.DiagnosticsFragment
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** XML references of the diagnostics journal, empty and filled, under the names of LT-4c's `DiagnosticsScreen` previews. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DiagnosticsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-settings")

    @Test
    fun empty() = journal("DiagnosticsScreen_empty", emptyList())

    @Test
    fun filled() = journal("DiagnosticsScreen_filled", ENTRIES)

    private fun journal(name: String, entries: List<DiagnosticEntry>) = references.fragment(
        name,
        ready = { fragment -> fragment.view?.findViewById<View>(R.id.loading)?.visibility == View.GONE }
    ) {
        DiagnosticsFragment().withViewModel {
            val diagnostics = RecordingDiagnostics().apply { this.entries.value = entries }
            DiagnosticsViewModel(diagnostics, FixedAcademicTime())
        }
    }

    private companion object {
        /** Newest first, as `AppDiagnostics.observe()` hands them; the error's trace stays collapsed. */
        val ENTRIES = listOf(
            DiagnosticEntry(
                Instant.parse("2026-10-07T08:41:05Z"), DiagnosticLevel.CRASH, "Crash",
                "IllegalStateException: Fragment SettingsFragment not attached to a context.",
                "java.lang.IllegalStateException: Fragment SettingsFragment not attached to a context.\n" +
                    "\tat androidx.fragment.app.Fragment.requireContext(Fragment.java:1027)"
            ),
            DiagnosticEntry(
                Instant.parse("2026-10-07T06:30:12Z"), DiagnosticLevel.ERROR, "Schedule",
                "Не удалось обновить расписание", "java.io.IOException: timeout"
            ),
            DiagnosticEntry(
                Instant.parse("2026-10-06T21:02:47Z"), DiagnosticLevel.WARNING, "Widgets",
                "Виджет расписания обновлён из кэша", null
            )
        )
    }
}
