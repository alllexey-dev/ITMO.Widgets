package dev.alllexey.itmowidgets.designsystem.theme

import android.content.Context
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * The Compose scheme of [ItmoTheme] equals, ARGB for ARGB, the MDC View theme the app's screens use, for every colour
 * role they read today: the 19 `?color*` attrs in `res` and every `R.attr.color*` read in Kotlin.
 * `colorControlHighlight` (the View ripple, black 12 % / white 20 %) has no scheme role; Compose ripples derive
 * from the content colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `platform light equals the DynamicColors theme`() =
        assertParity(ColorSource.Platform, dark = false, viewTheme(MaterialR.style.Theme_Material3_DynamicColors_DayNight))

    @Test
    @Config(qualifiers = "+night")
    fun `platform dark equals the DynamicColors theme`() =
        assertParity(ColorSource.Platform, dark = true, viewTheme(MaterialR.style.Theme_Material3_DynamicColors_DayNight))

    @Test
    fun `static light equals the baseline theme of API 26 to 30`() = assertParity(
        ColorSource.Static,
        dark = false,
        viewTheme(MaterialR.style.Theme_Material3_DayNight),
        belowApi35 = mapOf(
            Role.OnPrimaryContainer to MaterialR.color.m3_ref_palette_primary10,
            Role.OnSecondaryContainer to MaterialR.color.m3_ref_palette_secondary10,
            Role.OnTertiaryContainer to MaterialR.color.m3_ref_palette_tertiary10,
            Role.OnErrorContainer to MaterialR.color.m3_ref_palette_error10,
        ),
    )

    @Test
    @Config(qualifiers = "+night")
    fun `static dark equals the baseline theme of API 26 to 30`() =
        assertParity(ColorSource.Static, dark = true, viewTheme(MaterialR.style.Theme_Material3_DayNight))

    @Test
    fun `seed light equals the content-based DynamicColors theme`() =
        assertParity(ColorSource.Seed(SEED), dark = false, seededViewTheme())

    @Test
    @Config(qualifiers = "+night")
    fun `seed dark equals the content-based DynamicColors theme`() =
        assertParity(ColorSource.Seed(SEED), dark = true, seededViewTheme())

    @Test
    fun `dynamic light keeps the tone 10 error container below API 35`() {
        val view = viewTheme(MaterialR.style.Theme_Material3_DayNight)
        val scheme = staticColorScheme(dark = false).withViewThemeRoles(dark = false, sdk = 34)
        assertEquals(
            view.getColor(MaterialR.color.m3_ref_palette_error10).hex(),
            scheme.onErrorContainer.toArgb().hex(),
        )
    }

    /**
     * [belowApi35]: roles whose View value on API 26-30 is another palette colour than on the test's SDK 35. MDC's
     * `values-v35` moves the light baseline on-container roles from tone 10 to tone 30; the static scheme serves only
     * API 26-30 and iOS, so it keeps tone 10.
     */
    private fun assertParity(
        source: ColorSource,
        dark: Boolean,
        view: Context,
        belowApi35: Map<Role, Int> = emptyMap(),
    ) {
        var scheme: ColorScheme? = null
        compose.setContent {
            ItmoTheme(dark = dark, colorSource = source) { scheme = MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        val composed = checkNotNull(scheme) { "ItmoTheme did not compose" }
        val mismatches = Role.entries.mapNotNull { role ->
            val expected = belowApi35[role]?.let(view::getColor)
                ?: view.attrArgb(role.attr) ?: view.attrArgb(role.platformTwin)
                ?: error("${role.name} does not resolve in the View theme")
            val actual = role.of(composed).toArgb()
            if (expected == actual) null else "${role.name}: view ${expected.hex()} / compose ${actual.hex()}"
        }
        assertEquals("$source dark=$dark", emptyList<String>(), mismatches)
    }

    private fun viewTheme(style: Int): Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), style)

    /** What a debug host gets from `setContentBasedSource`; Robolectric only supports the wrapping path (SP-06). */
    private fun seededViewTheme(): Context = DynamicColors.wrapContextIfAvailable(
        viewTheme(MaterialR.style.Theme_Material3_DynamicColors_DayNight),
        DynamicColorsOptions.Builder().setContentBasedSource(SEED).build(),
    )

    private fun Context.attrArgb(attr: Int): Int? {
        if (attr == 0) return null
        val value = TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return null
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }

    private fun Int.hex() = "%08X".format(this)

    /**
     * A View colour attr and the scheme role it maps to. MDC's seeded wrapper drops AppCompat's `colorControlNormal`;
     * its `android:` twin carries the same value.
     */
    private enum class Role(val attr: Int, val of: (ColorScheme) -> androidx.compose.ui.graphics.Color, val platformTwin: Int = 0) {
        Primary(AppCompatR.attr.colorPrimary, { it.primary }),
        OnPrimary(MaterialR.attr.colorOnPrimary, { it.onPrimary }),
        PrimaryContainer(MaterialR.attr.colorPrimaryContainer, { it.primaryContainer }),
        OnPrimaryContainer(MaterialR.attr.colorOnPrimaryContainer, { it.onPrimaryContainer }),
        Secondary(MaterialR.attr.colorSecondary, { it.secondary }),
        OnSecondary(MaterialR.attr.colorOnSecondary, { it.onSecondary }),
        SecondaryContainer(MaterialR.attr.colorSecondaryContainer, { it.secondaryContainer }),
        OnSecondaryContainer(MaterialR.attr.colorOnSecondaryContainer, { it.onSecondaryContainer }),
        Tertiary(MaterialR.attr.colorTertiary, { it.tertiary }),
        OnTertiary(MaterialR.attr.colorOnTertiary, { it.onTertiary }),
        TertiaryContainer(MaterialR.attr.colorTertiaryContainer, { it.tertiaryContainer }),
        OnTertiaryContainer(MaterialR.attr.colorOnTertiaryContainer, { it.onTertiaryContainer }),
        Error(AppCompatR.attr.colorError, { it.error }),
        OnError(MaterialR.attr.colorOnError, { it.onError }),
        ErrorContainer(MaterialR.attr.colorErrorContainer, { it.errorContainer }),
        OnErrorContainer(MaterialR.attr.colorOnErrorContainer, { it.onErrorContainer }),
        Background(android.R.attr.colorBackground, { it.background }),
        OnBackground(MaterialR.attr.colorOnBackground, { it.onBackground }),
        Surface(MaterialR.attr.colorSurface, { it.surface }),
        OnSurface(MaterialR.attr.colorOnSurface, { it.onSurface }),
        SurfaceVariant(MaterialR.attr.colorSurfaceVariant, { it.surfaceVariant }),
        OnSurfaceVariant(MaterialR.attr.colorOnSurfaceVariant, { it.onSurfaceVariant }),
        SurfaceContainerLowest(MaterialR.attr.colorSurfaceContainerLowest, { it.surfaceContainerLowest }),
        SurfaceContainerLow(MaterialR.attr.colorSurfaceContainerLow, { it.surfaceContainerLow }),
        SurfaceContainer(MaterialR.attr.colorSurfaceContainer, { it.surfaceContainer }),
        SurfaceContainerHigh(MaterialR.attr.colorSurfaceContainerHigh, { it.surfaceContainerHigh }),
        SurfaceContainerHighest(MaterialR.attr.colorSurfaceContainerHighest, { it.surfaceContainerHighest }),
        Outline(MaterialR.attr.colorOutline, { it.outline }),
        OutlineVariant(MaterialR.attr.colorOutlineVariant, { it.outlineVariant }),
        ControlNormal(AppCompatR.attr.colorControlNormal, { it.onSurfaceVariant }, android.R.attr.colorControlNormal),
    }

    private companion object {
        const val SEED = 0xff087f5b.toInt()
    }
}
