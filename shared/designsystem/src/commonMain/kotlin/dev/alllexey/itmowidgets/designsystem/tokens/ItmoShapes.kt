package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii and card geometry in dp: the M3E corner scale (MDC 1.13's M3 scale plus `largeIncreased`,
 * `extraLargeIncreased` and `extraExtraLarge`) and the app's named shapes. `res/values/dimens.xml` keeps the View
 * screens' copies; `DesignTokensParityTest` keeps the shared ones equal and lists the M3E divergences.
 */
object ShapeTokens {
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val LargeIncreased: Dp = 20.dp
    val ExtraLarge: Dp = 28.dp
    val ExtraLargeIncreased: Dp = 32.dp
    val ExtraExtraLarge: Dp = 48.dp

    /** `Card.Content`, `Card.CompactSummary` and the settings cards. */
    val CardContent: Dp = LargeIncreased

    /**
     * `Card.Summary`, the sport score card, as `cardHero` while it is the page's hero (owner, item 14 Q2 (a)); the
     * View screens keep 24 dp (`design_card_radius_summary`).
     */
    val CardSummary: Dp = ExtraLarge

    /** `Card.Hero`. */
    val CardHero: Dp = ExtraLarge

    /** `Card.ScheduleDay`. */
    val ScheduleDay: Dp = Large

    /** The outer corners of a connected group's first and last rows. */
    val GroupOuter: Dp = LargeIncreased

    /** Every corner between two rows of a connected group. */
    val GroupInner: Dp = ExtraSmall

    /** The gap between two rows of a connected group. */
    val GroupGap: Dp = 2.dp

    /** The stroke of an outlined card. */
    val CardStroke: Dp = 1.dp

    /** Every card is flat. */
    val CardElevation: Dp = 0.dp
}

/** [ShapeTokens] as shapes: the corner scale for components and the app's card family. */
@Immutable
data class ItmoShapes(
    val extraSmall: CornerBasedShape,
    val small: CornerBasedShape,
    val medium: CornerBasedShape,
    val large: CornerBasedShape,
    val largeIncreased: CornerBasedShape,
    val extraLarge: CornerBasedShape,
    val extraLargeIncreased: CornerBasedShape,
    val extraExtraLarge: CornerBasedShape,
    val full: CornerBasedShape,
    val cardContent: CornerBasedShape,
    val cardSummary: CornerBasedShape,
    val cardHero: CornerBasedShape,
    val scheduleDay: CornerBasedShape,
    val groupOuterRadius: Dp,
    val groupInnerRadius: Dp,
    val groupGap: Dp,
    val cardStroke: Dp,
    val cardElevation: Dp,
) {
    companion object {
        val Default = ItmoShapes(
            extraSmall = RoundedCornerShape(ShapeTokens.ExtraSmall),
            small = RoundedCornerShape(ShapeTokens.Small),
            medium = RoundedCornerShape(ShapeTokens.Medium),
            large = RoundedCornerShape(ShapeTokens.Large),
            largeIncreased = RoundedCornerShape(ShapeTokens.LargeIncreased),
            extraLarge = RoundedCornerShape(ShapeTokens.ExtraLarge),
            extraLargeIncreased = RoundedCornerShape(ShapeTokens.ExtraLargeIncreased),
            extraExtraLarge = RoundedCornerShape(ShapeTokens.ExtraExtraLarge),
            full = CircleShape,
            cardContent = RoundedCornerShape(ShapeTokens.CardContent),
            cardSummary = RoundedCornerShape(ShapeTokens.CardSummary),
            cardHero = RoundedCornerShape(ShapeTokens.CardHero),
            scheduleDay = RoundedCornerShape(ShapeTokens.ScheduleDay),
            groupOuterRadius = ShapeTokens.GroupOuter,
            groupInnerRadius = ShapeTokens.GroupInner,
            groupGap = ShapeTokens.GroupGap,
            cardStroke = ShapeTokens.CardStroke,
            cardElevation = ShapeTokens.CardElevation,
        )

        /**
         * The set of `ItmoPlatformStyle.Ios`: every card is an inset group ([IosMetrics.insetGroupRadius]) and a
         * connected group's rows touch with square inner corners, as rows of an inset-grouped list do (a separator
         * parts them). The corner scale stays [Default]'s, so Material components keep their geometry.
         */
        val Ios = Default.copy(
            cardContent = RoundedCornerShape(IosMetrics.insetGroupRadius),
            cardSummary = RoundedCornerShape(IosMetrics.insetGroupRadius),
            cardHero = RoundedCornerShape(IosMetrics.insetGroupRadius),
            scheduleDay = RoundedCornerShape(IosMetrics.insetGroupRadius),
            groupOuterRadius = IosMetrics.insetGroupRadius,
            groupInnerRadius = 0.dp,
            groupGap = 0.dp,
        )
    }
}

/** Material's shape scale from the same tokens, so Material components round like the kit. */
internal fun ItmoShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = extraSmall,
    small = small,
    medium = medium,
    large = large,
    extraLarge = extraLarge,
    largeIncreased = largeIncreased,
    extraLargeIncreased = extraLargeIncreased,
    extraExtraLarge = extraExtraLarge,
)

internal val LocalItmoShapes = staticCompositionLocalOf { ItmoShapes.Default }
