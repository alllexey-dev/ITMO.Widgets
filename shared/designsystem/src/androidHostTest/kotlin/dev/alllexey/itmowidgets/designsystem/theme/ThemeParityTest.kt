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
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
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
 * from the content colour. The static scheme is the one divergence (owner, item 14 Q1): the brand blue's TonalSpot
 * scheme, checked against MDC's own `SchemeTonalSpot`, while the View screens and widgets keep the M3 baseline on
 * API 26-30, which [baselineColorScheme] still equals.
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
    fun `static light is MDC's TonalSpot scheme of the brand seed`() = assertBrand(dark = false)

    @Test
    @Config(qualifiers = "+night")
    fun `static dark is MDC's TonalSpot scheme of the brand seed`() = assertBrand(dark = true)

    @Test
    fun `baseline light equals the View theme of API 26 to 30`() = assertParity(
        baselineColorScheme(dark = false),
        "baseline dark=false",
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
    fun `baseline dark equals the View theme of API 26 to 30`() = assertParity(
        baselineColorScheme(dark = true),
        "baseline dark=true",
        viewTheme(MaterialR.style.Theme_Material3_DayNight),
    )

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
        val scheme = baselineColorScheme(dark = false).withViewThemeRoles(dark = false, sdk = 34)
        assertEquals(
            view.getColor(MaterialR.color.m3_ref_palette_error10).hex(),
            scheme.onErrorContainer.toArgb().hex(),
        )
    }

    private fun assertParity(source: ColorSource, dark: Boolean, view: Context) =
        assertParity(themeScheme(source, dark), "$source dark=$dark", view)

    /**
     * [belowApi35]: roles whose View value on API 26-30 is another palette colour than on the test's SDK 35. MDC's
     * `values-v35` moves the light baseline on-container roles from tone 10 to tone 30; the baseline scheme serves
     * only API 26-30, so it keeps tone 10.
     */
    private fun assertParity(
        composed: ColorScheme,
        label: String,
        view: Context,
        belowApi35: Map<Role, Int> = emptyMap(),
    ) {
        val mismatches = Role.entries.mapNotNull { role ->
            val expected = belowApi35[role]?.let(view::getColor)
                ?: view.attrArgb(role.attr) ?: view.attrArgb(role.platformTwin)
                ?: error("${role.name} does not resolve in the View theme")
            val actual = role.of(composed).toArgb()
            if (expected == actual) null else "${role.name}: view ${expected.hex()} / compose ${actual.hex()}"
        }
        assertEquals(label, emptyList<String>(), mismatches)
    }

    /** Every role [ItmoTheme]'s static scheme shares with MDC's 2021 `SchemeTonalSpot` of [BRAND_SEED]. */
    private fun assertBrand(dark: Boolean) {
        val composed = themeScheme(ColorSource.Static, dark)
        val mdc = SchemeTonalSpot(Hct.fromInt(BRAND_SEED), dark, 0.0)
        val colors = MaterialDynamicColors()
        val mismatches = brandRoles(colors).mapNotNull { (name, role, dynamic) ->
            val expected = dynamic.getArgb(mdc)
            val actual = role(composed).toArgb()
            if (expected == actual) null else "$name: MDC ${expected.hex()} / compose ${actual.hex()}"
        }
        assertEquals("brand dark=$dark", emptyList<String>(), mismatches)
    }

    private fun themeScheme(source: ColorSource, dark: Boolean): ColorScheme {
        var scheme: ColorScheme? = null
        compose.setContent {
            ItmoTheme(dark = dark, colorSource = source) { scheme = MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        return checkNotNull(scheme) { "ItmoTheme did not compose" }
    }

    private fun brandRoles(
        colors: MaterialDynamicColors,
    ): List<Triple<String, (ColorScheme) -> androidx.compose.ui.graphics.Color, DynamicColor>> = listOf(
        Triple("primary", { it.primary }, colors.primary()),
        Triple("onPrimary", { it.onPrimary }, colors.onPrimary()),
        Triple("primaryContainer", { it.primaryContainer }, colors.primaryContainer()),
        Triple("onPrimaryContainer", { it.onPrimaryContainer }, colors.onPrimaryContainer()),
        Triple("inversePrimary", { it.inversePrimary }, colors.inversePrimary()),
        Triple("secondary", { it.secondary }, colors.secondary()),
        Triple("onSecondary", { it.onSecondary }, colors.onSecondary()),
        Triple("secondaryContainer", { it.secondaryContainer }, colors.secondaryContainer()),
        Triple("onSecondaryContainer", { it.onSecondaryContainer }, colors.onSecondaryContainer()),
        Triple("tertiary", { it.tertiary }, colors.tertiary()),
        Triple("onTertiary", { it.onTertiary }, colors.onTertiary()),
        Triple("tertiaryContainer", { it.tertiaryContainer }, colors.tertiaryContainer()),
        Triple("onTertiaryContainer", { it.onTertiaryContainer }, colors.onTertiaryContainer()),
        Triple("error", { it.error }, colors.error()),
        Triple("onError", { it.onError }, colors.onError()),
        Triple("errorContainer", { it.errorContainer }, colors.errorContainer()),
        Triple("onErrorContainer", { it.onErrorContainer }, colors.onErrorContainer()),
        Triple("background", { it.background }, colors.background()),
        Triple("onBackground", { it.onBackground }, colors.onBackground()),
        Triple("surface", { it.surface }, colors.surface()),
        Triple("onSurface", { it.onSurface }, colors.onSurface()),
        Triple("surfaceVariant", { it.surfaceVariant }, colors.surfaceVariant()),
        Triple("onSurfaceVariant", { it.onSurfaceVariant }, colors.onSurfaceVariant()),
        Triple("inverseSurface", { it.inverseSurface }, colors.inverseSurface()),
        Triple("inverseOnSurface", { it.inverseOnSurface }, colors.inverseOnSurface()),
        Triple("outline", { it.outline }, colors.outline()),
        Triple("outlineVariant", { it.outlineVariant }, colors.outlineVariant()),
        Triple("surfaceDim", { it.surfaceDim }, colors.surfaceDim()),
        Triple("surfaceBright", { it.surfaceBright }, colors.surfaceBright()),
        Triple("surfaceContainerLowest", { it.surfaceContainerLowest }, colors.surfaceContainerLowest()),
        Triple("surfaceContainerLow", { it.surfaceContainerLow }, colors.surfaceContainerLow()),
        Triple("surfaceContainer", { it.surfaceContainer }, colors.surfaceContainer()),
        Triple("surfaceContainerHigh", { it.surfaceContainerHigh }, colors.surfaceContainerHigh()),
        Triple("surfaceContainerHighest", { it.surfaceContainerHighest }, colors.surfaceContainerHighest()),
    )

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
