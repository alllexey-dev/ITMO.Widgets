package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.BRAND_SEED
import dev.alllexey.itmowidgets.designsystem.theme.ExtendedColorTokens
import dev.alllexey.itmowidgets.designsystem.theme.LocalItmoExpressive
import dev.alllexey.itmowidgets.designsystem.theme.LocalItmoExtendedColors
import dev.alllexey.itmowidgets.designsystem.theme.baselineColorScheme
import dev.alllexey.itmowidgets.designsystem.theme.resolve
import dev.alllexey.itmowidgets.designsystem.theme.seededColorScheme
import dev.alllexey.itmowidgets.designsystem.theme.staticColorScheme

/*
 * The M3E value candidates of the owner's item 14 (M3-02a): each named [M3eValues] set is one combination of answers,
 * rendered into contact sheets by the screenshot harness (`-Pshots.variant=<name>`). The owner took `recommended`
 * (2026-10-09) and M3-02 made it the default tokens, so a sheet of it equals the baselines; `parity` keeps the 2.2
 * look and the other sets the rejected answers, for side-by-side checks until the harness drops variants. Nothing
 * reads them unless the harness selects one.
 */

/** Selecting an M3E candidate: only the screenshot harness does, for the owner's contact sheets. */
@RequiresOptIn(message = "M3E candidates render only in the owner's contact sheets (M3-02a); screens never pick one.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class M3eCandidateApi

/** The candidate [ItmoPreview][dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview] renders in; null: none. */
@M3eCandidateApi
val LocalM3eCandidate = staticCompositionLocalOf<String?> { null }

/** The candidate names `-Pshots.variant` accepts. */
@M3eCandidateApi
object M3eCandidates {
    val names: List<String> get() = M3eCandidateSets.byName.keys.toList()
}

/** Where a candidate's colour scheme comes from. */
internal enum class M3eColors {
    /** What [ItmoTheme][dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme] gives: dynamic on Android 12+. */
    Platform,

    /** [Platform] with the M3 baseline `#6750A4` in place of the brand static scheme: the 2.2 look. */
    PlatformOverBaseline,

    /** The M3 baseline `#6750A4` static scheme, 2.2's look on API 26-30 and iOS. */
    BaselineStatic,

    /** Static schemes from [BRAND_SEED], Content variant (primary stays close to the seed). */
    BrandContent,

    /** Static schemes from [BRAND_SEED], TonalSpot variant (Material's default, calmer roles): the default. */
    BrandTonalSpot,
}

/** How much of M3E the kit shows. */
internal enum class M3eIntensity {
    /** The expressive switch off: 2.2's components. */
    Parity,

    /** The switch on, components on the standard motion scheme, expressive motion only for heroes. */
    Foundational,

    /** The switch on and every component on `MotionScheme.expressive()` (bouncy springs). */
    ExpressiveMotion,
}

/** One answer set of item 14: colours below Android 12 and on iOS, the summary radius, tab labels, intensity. */
@Immutable
internal data class M3eValues(
    val colors: M3eColors,
    val cardSummary: Dp,
    val navigationLabelsOnAll: Boolean,
    val intensity: M3eIntensity,
)

internal object M3eCandidateSets {
    /** 2.2's values: the M3 baseline below Android 12, the 24 dp summary, the selected label only, the switch off. */
    val Parity = M3eValues(
        colors = M3eColors.PlatformOverBaseline,
        cardSummary = 24.dp,
        navigationLabelsOnAll = false,
        intensity = M3eIntensity.Parity,
    )

    /**
     * The packet's recommendation as API 26-30 and iOS see it, the owner's answer and the default tokens since M3-02;
     * each other set changes one answer of it.
     */
    val Recommended = M3eValues(
        colors = M3eColors.BrandTonalSpot,
        cardSummary = ShapeTokens.ExtraLarge,
        navigationLabelsOnAll = true,
        intensity = M3eIntensity.Foundational,
    )

    val byName: Map<String, M3eValues> = linkedMapOf(
        "parity" to Parity,
        "recommended" to Recommended,
        "recommended-dynamic" to Recommended.copy(colors = M3eColors.Platform),
        "colors-baseline" to Recommended.copy(colors = M3eColors.BaselineStatic),
        "colors-brand-content" to Recommended.copy(colors = M3eColors.BrandContent),
        "card-summary-20" to Recommended.copy(cardSummary = ShapeTokens.LargeIncreased),
        "card-summary-24" to Recommended.copy(cardSummary = 24.dp),
        "labels-selected" to Recommended.copy(navigationLabelsOnAll = false),
        "intensity-parity" to Recommended.copy(intensity = M3eIntensity.Parity),
        "intensity-expressive" to Recommended.copy(intensity = M3eIntensity.ExpressiveMotion),
    )
}

internal val LocalM3eValues = staticCompositionLocalOf<M3eValues?> { null }

/**
 * Inside an [ItmoTheme][dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme], re-provides the scheme, shapes,
 * expressive switch and motion of the candidate [LocalM3eCandidate] names; without one, just [content].
 */
@OptIn(M3eCandidateApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProvideM3eCandidate(dark: Boolean, content: @Composable () -> Unit) {
    val name = LocalM3eCandidate.current ?: return content()
    val values = checkNotNull(M3eCandidateSets.byName[name]) {
        "Unknown M3E candidate '$name'; one of ${M3eCandidates.names}"
    }
    val platform = MaterialTheme.colorScheme
    val scheme = remember(values.colors, dark, platform) { values.colors.scheme(dark, platform) }
    val extended = remember(scheme, dark) { ExtendedColorTokens.of(dark).resolve(scheme) }
    val shapes = remember(values.cardSummary) {
        ItmoShapes.Default.copy(cardSummary = RoundedCornerShape(values.cardSummary))
    }
    val expressive = values.intensity != M3eIntensity.Parity
    val motion = if (values.intensity == M3eIntensity.ExpressiveMotion) ExpressiveMotion else ItmoMotion.Default.scheme
    val typography = MaterialTheme.typography
    CompositionLocalProvider(
        LocalM3eValues provides values,
        LocalItmoExtendedColors provides extended,
        LocalItmoShapes provides shapes,
        LocalItmoMotion provides ItmoMotion.Default.copy(scheme = motion),
        LocalItmoExpressive provides expressive,
    ) {
        val materialShapes = shapes.toMaterialShapes()
        if (expressive) {
            MaterialExpressiveTheme(
                colorScheme = scheme,
                motionScheme = motion,
                shapes = materialShapes,
                typography = typography,
                content = content,
            )
        } else {
            MaterialTheme(
                colorScheme = scheme,
                motionScheme = motion,
                shapes = materialShapes,
                typography = typography,
                content = content,
            )
        }
    }
}

/** Whether a navigation bar shows the label on the selected tab only: [parity] unless a candidate decides. */
@Composable
@ReadOnlyComposable
internal fun navigationLabelsOnSelectedOnly(parity: Boolean): Boolean =
    LocalM3eValues.current?.let { !it.navigationLabelsOnAll } ?: parity

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveMotion = MotionScheme.expressive()

private fun M3eColors.scheme(dark: Boolean, platform: ColorScheme): ColorScheme = when (this) {
    M3eColors.Platform -> platform
    M3eColors.PlatformOverBaseline ->
        if (platform.primary == staticColorScheme(dark).primary) baselineColorScheme(dark) else platform
    M3eColors.BaselineStatic -> baselineColorScheme(dark)
    M3eColors.BrandContent -> seededColorScheme(BRAND_SEED, dark)
    M3eColors.BrandTonalSpot -> staticColorScheme(dark)
}
