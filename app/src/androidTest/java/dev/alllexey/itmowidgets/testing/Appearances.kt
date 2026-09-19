package dev.alllexey.itmowidgets.testing

import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.SportScoreCollapsePreviewActivity
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdatePreviewActivity

/**
 * The appearance matrix shared by the visual tests.
 *
 * Locally only the first (light) entry runs. The full four-entry matrix is enabled with the
 * instrumentation argument `appearanceMatrix=full`:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.appearanceMatrix=full
 * ```
 */
object Appearances {
    const val ARGUMENT = "appearanceMatrix"

    /** One appearance; the debug hosts each take their own subset of these fields. */
    data class Spec(
        val name: String,
        val dark: Boolean = false,
        val fontScale: Float = 1f,
        val colorSeed: Int? = null,
        val widthDp: Int = 0
    )

    val all: List<Spec> = listOf(
        Spec("light"),
        Spec("dark", dark = true),
        Spec("green-narrow", fontScale = 1.3f, colorSeed = 0xff087f5b.toInt(), widthDp = 320),
        Spec("dark-narrow", dark = true, fontScale = 1.3f, colorSeed = 0xff826c24.toInt(), widthDp = 320)
    )

    val light: Spec get() = all.first()

    val fullMatrix: Boolean
        get() = InstrumentationRegistry.getArguments().getString(ARGUMENT) == "full"

    /** [all] when the full matrix was requested, otherwise only the light entry. */
    val default: List<Spec> get() = if (fullMatrix) all else all.take(1)

    fun Spec.toSettingsNavigation() =
        SettingsNavigationTestActivity.Appearance(dark = dark, fontScale = fontScale, colorSeed = colorSeed)

    fun Spec.toSettingsPreview() = SettingsPreviewActivity.Appearance(fontScale = fontScale, dark = dark)

    fun Spec.toAppUpdate() =
        AppUpdatePreviewActivity.Appearance(widthDp = widthDp, fontScale = fontScale, dark = dark, colorSeed = colorSeed)

    fun Spec.toSportCards() =
        SportCardsPreviewActivity.Appearance(fontScale = fontScale, dark = dark, widthDp = widthDp, colorSeed = colorSeed)

    fun Spec.toSportScoreCollapse() =
        SportScoreCollapsePreviewActivity.Appearance(fontScale = fontScale, dark = dark, seedColor = colorSeed)

    fun Spec.toRecordbook() =
        RecordbookPreviewActivity.Appearance(fontScale = fontScale, dark = dark, widthDp = widthDp, colorSeed = colorSeed)
}
