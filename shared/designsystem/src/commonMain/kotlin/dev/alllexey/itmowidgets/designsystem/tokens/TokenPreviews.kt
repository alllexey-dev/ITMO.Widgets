package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoExtendedColors
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

// Goldens of the token sets beside ColorRolesPreview: a token change (the M3E values) shows here first. Labels are
// token names, not user-visible text. Windows are as tall as the lists at 1.3, so no row is cut off.

/** The app's own colours as they are used: each accent on the surface or container it sits on. */
@Preview(heightDp = 900)
@Composable
private fun TokenExtendedColorsPreview() = ItmoPreview {
    val colors = ItmoTheme.extendedColors
    val card = ItmoTheme.colorScheme.surfaceContainerLow
    TokenColumn {
        accentsOnSurface(colors).forEach { (name, accent) -> AccentRow(name, accent, card) }
        conditions(colors).forEach { (name, pair) -> AccentRow(name, pair.first, pair.second) }
    }
}

/** Every M3 type role and its emphasized twin, with size, line height and weight. */
@Preview(heightDp = 1150)
@Composable
private fun TokenTypographyPreview() = ItmoPreview {
    // Short, so the largest role fits 320 dp at 1.3 next to its twin.
    val sample = "Аб"
    val emphasized = ItmoTheme.emphasizedTypography
    val roles = materialRoles(ItmoTheme.typography).zip(emphasizedRoles(emphasized))
    TokenColumn {
        roles.forEach { (role, twin) ->
            val (name, style) = role
            val token = TypeScaleTokens.roles.getValue(name).first
            Column {
                TokenLabel("$name ${token.size.toInt()}/${token.lineHeight.toInt()} ${token.weight}")
                Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group)) {
                    Text(sample, color = ItmoTheme.colorScheme.onSurface, style = style)
                    Text(sample, color = ItmoTheme.colorScheme.onSurface, style = twin.second)
                }
            }
        }
    }
}

/** The corner scale, the card family and a connected group's outer, inner and gap values. */
@Preview(heightDp = 1200)
@Composable
private fun TokenShapesPreview() = ItmoPreview {
    val shapes = ItmoTheme.shapes
    TokenColumn {
        shapeScale(shapes).forEach { (name, shape) -> ShapeRow(name, shape) }
        TokenLabel("group ${shapes.groupOuterRadius.value.toInt()}/${shapes.groupInnerRadius.value.toInt()}")
        Column(verticalArrangement = Arrangement.spacedBy(shapes.groupGap)) {
            val outer = shapes.groupOuterRadius
            val inner = shapes.groupInnerRadius
            GroupRow(RoundedCornerShape(outer, outer, inner, inner))
            GroupRow(RoundedCornerShape(inner))
            GroupRow(RoundedCornerShape(inner, inner, outer, outer))
        }
    }
}

@Composable
private fun TokenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) { content() }
}

@Composable
private fun TokenLabel(text: String) {
    Text(text, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelSmall)
}

/** An accent as a lesson strip and a dot on [surface], the way cards and chips carry it. */
@Composable
private fun AccentRow(name: String, accent: Color, surface: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .size(width = SwatchWidth, height = SwatchHeight)
                .clip(ItmoTheme.shapes.small)
                .background(surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(StripWidth).fillMaxHeight().background(accent))
            Spacer(Modifier.width(ItmoTheme.spacing.compact))
            Box(Modifier.size(DotSize).clip(ItmoTheme.shapes.full).background(accent))
        }
        Text(
            name,
            Modifier.padding(start = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ShapeRow(name: String, shape: CornerBasedShape) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = ShapeWidth, height = ShapeHeight)
                .clip(shape)
                .background(ItmoTheme.colorScheme.primaryContainer),
        )
        Text(
            name,
            Modifier.padding(start = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GroupRow(shape: CornerBasedShape) {
    Box(
        Modifier
            .size(width = ShapeWidth * 2, height = ItmoTheme.spacing.touchTarget)
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .border(ItmoTheme.shapes.cardStroke, ItmoTheme.colorScheme.outlineVariant, shape),
    )
}

private fun accentsOnSurface(colors: ItmoExtendedColors): List<Pair<String, Color>> = with(colors) {
    listOf(
        "lessonTypeLecture" to lessonTypeLecture,
        "lessonTypeLab" to lessonTypeLab,
        "lessonTypePractice" to lessonTypePractice,
        "lessonTypeAssessment" to lessonTypeAssessment,
        "lessonTypeConsultation" to lessonTypeConsultation,
        "lessonTypeSport" to lessonTypeSport,
        "lessonTypeFree" to lessonTypeFree,
        "lessonTypeDefault" to lessonTypeDefault,
        "recordbookPassed" to recordbookPassed,
        "sportScoreAttendance" to sportScoreAttendance,
        "sportScoreBonus" to sportScoreBonus,
        "teacherLevelVeryNegative" to teacherLevelVeryNegative,
        "teacherLevelNegative" to teacherLevelNegative,
        "teacherLevelMixed" to teacherLevelMixed,
        "teacherLevelPositive" to teacherLevelPositive,
        "teacherLevelVeryPositive" to teacherLevelVeryPositive,
    )
}

private fun conditions(colors: ItmoExtendedColors): List<Pair<String, Pair<Color, Color>>> = with(colors) {
    listOf(
        "sportConditionAllowed" to (sportConditionAllowed to sportConditionAllowedContainer),
        "sportConditionWaiting" to (sportConditionWaiting to sportConditionWaitingContainer),
        "sportConditionWarning" to (sportConditionWarning to sportConditionWarningContainer),
        "sportConditionBlocked" to (sportConditionBlocked to sportConditionBlockedContainer),
    )
}

private fun materialRoles(typography: Typography): List<Pair<String, TextStyle>> =
    with(typography) {
        listOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall,
        )
    }

private fun emphasizedRoles(typography: ItmoEmphasizedTypography): List<Pair<String, TextStyle>> = with(typography) {
    listOf(
        "displayLarge" to displayLarge,
        "displayMedium" to displayMedium,
        "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge,
        "headlineMedium" to headlineMedium,
        "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge,
        "titleMedium" to titleMedium,
        "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge,
        "bodyMedium" to bodyMedium,
        "bodySmall" to bodySmall,
        "labelLarge" to labelLarge,
        "labelMedium" to labelMedium,
        "labelSmall" to labelSmall,
    )
}

private fun shapeScale(shapes: ItmoShapes): List<Pair<String, CornerBasedShape>> = with(shapes) {
    listOf(
        "extraSmall ${ShapeTokens.ExtraSmall.value.toInt()}" to extraSmall,
        "small ${ShapeTokens.Small.value.toInt()}" to small,
        "medium ${ShapeTokens.Medium.value.toInt()}" to medium,
        "large ${ShapeTokens.Large.value.toInt()}" to large,
        "largeIncreased ${ShapeTokens.LargeIncreased.value.toInt()}" to largeIncreased,
        "extraLarge ${ShapeTokens.ExtraLarge.value.toInt()}" to extraLarge,
        "extraLargeIncreased ${ShapeTokens.ExtraLargeIncreased.value.toInt()}" to extraLargeIncreased,
        "extraExtraLarge ${ShapeTokens.ExtraExtraLarge.value.toInt()}" to extraExtraLarge,
        "full" to full,
        "cardContent ${ShapeTokens.CardContent.value.toInt()}" to cardContent,
        "cardSummary ${ShapeTokens.CardSummary.value.toInt()}" to cardSummary,
        "cardHero ${ShapeTokens.CardHero.value.toInt()}" to cardHero,
        "scheduleDay ${ShapeTokens.ScheduleDay.value.toInt()}" to scheduleDay,
    )
}

private val SwatchWidth = 72.dp
private val SwatchHeight = 32.dp
private val StripWidth = 4.dp
private val DotSize = 12.dp
private val ShapeWidth = 96.dp
private val ShapeHeight = 56.dp
