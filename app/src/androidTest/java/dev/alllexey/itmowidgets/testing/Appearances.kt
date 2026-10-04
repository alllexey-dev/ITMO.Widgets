package dev.alllexey.itmowidgets.testing

import android.content.res.Configuration
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.util.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

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

    /** One appearance; the debug hosts each read their own subset of these fields. */
    data class Spec(
        val name: String,
        val dark: Boolean = false,
        val fontScale: Float = 1f,
        val colorSeed: Int? = null,
        val widthDp: Int = 0
    ) {
        fun toPreview(): PreviewAppearance = PreviewAppearance(fontScale, dark, widthDp, colorSeed)
    }

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

    fun Spec.assertEffective(root: View, defaultPrimary: MutableMap<Boolean, Int>) {
        val config = root.resources.configuration
        assertEquals("Effective font scale for $name", fontScale, config.fontScale, 0.001f)
        assertEquals("Effective night mode for $name",
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
            config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        val primary = root.context.color.primary
        if (colorSeed == null) defaultPrimary[dark] = primary
        else assertNotEquals("The seeded palette must differ from the ordinary $name theme",
            checkNotNull(defaultPrimary[dark]), primary)
    }
}
