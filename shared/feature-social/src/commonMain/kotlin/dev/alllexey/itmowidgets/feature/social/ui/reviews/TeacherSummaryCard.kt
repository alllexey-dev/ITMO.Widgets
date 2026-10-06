package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.reviews.tone
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ToneDot
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_auto_awesome
import dev.alllexey.itmowidgets.shared.designsystem.ic_remove
import dev.alllexey.itmowidgets.shared.designsystem.ic_summary_collapse
import dev.alllexey.itmowidgets.shared.designsystem.ic_summary_expand
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_ai
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_ai_description
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_collapsed
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_cons
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_expanded
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_label
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_less
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_list
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_more
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_pros
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_scale_description
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * Backend's AI summary of a teacher's reviews, first in the reviews section (port of `SummaryViews.kt` and
 * `item_teacher_summary.xml`): the header with the review count and `ИИ`, the tone (only when [TeacherSummary.showsLevel],
 * never at low confidence), the description, pros and cons, tags and the five scales folded behind a text button.
 * Texts are plain; empty blocks are hidden. Whether the scales are open ([expanded]) belongs to the screen, which
 * [onToggle] asks to flip it; opening them only grows the card.
 */
@Composable
fun TeacherSummaryCard(
    summary: TeacherSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ItmoTheme.shapes.cardContent)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .testTag(TeacherReviewTestTags.SUMMARY),
    ) {
        Column(
            Modifier.padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.cardPadding,
                end = ItmoTheme.spacing.cardPadding,
            ),
        ) {
            SummaryHeader(summary.reviewCount)
            if (summary.showsLevel) LevelRow(summary.level)
            Text(
                summary.description,
                Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact),
                style = ItmoTheme.typography.bodyMedium,
                color = ItmoTheme.colorScheme.onSurface,
            )
            Points(summary.pros, KitRes.drawable.ic_add, Res.string.teacher_summary_pros, ItmoTheme.spacing.content)
            // Without pros the cons start the block after the description.
            Points(
                summary.cons,
                KitRes.drawable.ic_remove,
                Res.string.teacher_summary_cons,
                if (summary.pros.isEmpty()) ItmoTheme.spacing.content else ItmoTheme.spacing.related,
            )
            if (summary.tags.isNotEmpty()) Tags(summary.tags.map { it.label() })
            if (expanded) Scales(summary.scales)
        }
        ScalesToggle(expanded, onToggle)
    }
}

@Composable
private fun SummaryHeader(reviewCount: Int) {
    val title = pluralStringResource(Res.plurals.teacher_summary_label, reviewCount, reviewCount)
    val description = "$title, ${stringResource(Res.string.teacher_summary_ai_description)}"
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_auto_awesome),
            contentDescription = null,
            modifier = Modifier.size(HeaderIconSize),
            tint = ItmoTheme.colorScheme.primary,
        )
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = ItmoTheme.spacing.compact),
            style = ItmoTheme.typography.titleSmall,
            color = ItmoTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.teacher_summary_ai),
            style = ItmoTheme.typography.bodyMedium,
            color = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The dot and the words of the tone, read as `Тон отзывов: ...`. */
@Composable
private fun LevelRow(level: TeacherLevel) {
    val tone = level.tone()
    val label = tone.label.asString()
    val description = tone.description(label).asString()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.content)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneDot(level.color(), Modifier.padding(horizontal = DotMargin))
        Text(
            label,
            Modifier.weight(1f).padding(start = ItmoTheme.spacing.compact),
            style = ItmoTheme.typography.labelLarge,
            color = ItmoTheme.colorScheme.onSurface,
        )
    }
}

/** One row per point with a small plus or minus centred on its first line; the block reads `Плюсы: ...`. */
@Composable
private fun Points(points: List<String>, icon: DrawableResource, title: StringResource, top: Dp) {
    if (points.isEmpty()) return
    val description = stringResource(Res.string.teacher_summary_list, stringResource(title), points.joinToString("; "))
    val style = ItmoTheme.typography.bodyMedium
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = top)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        points.forEach { point ->
            Row(Modifier.fillMaxWidth().padding(vertical = PointPadding)) {
                // The icon box is one text line tall, so the sign stays centred on the first line at any font scale.
                Box(Modifier.width(PointIconSize).height(lineHeight), contentAlignment = Alignment.Center) {
                    // `scaleType="center"`: the 24 dp glyph unscaled; its sign fits the 16 dp column.
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(PointGlyphSize),
                        tint = ItmoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    point,
                    Modifier.weight(1f).padding(start = ItmoTheme.spacing.compact),
                    style = style,
                    color = ItmoTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Tag chips: a 28 dp outlined label that does nothing on touch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(labels: List<StringResource>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.content),
        horizontalArrangement = Arrangement.spacedBy(TagSpacing),
        verticalArrangement = Arrangement.spacedBy(TagSpacing),
    ) {
        labels.forEach { label ->
            Box(
                Modifier
                    .heightIn(min = TagHeight)
                    .border(BorderStroke(TagStroke, ItmoTheme.colorScheme.outlineVariant), ItmoTheme.shapes.small)
                    .padding(horizontal = TagPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = ItmoTheme.typography.labelMedium,
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The five scales in [SummaryScaleKind] order; a missing one reads `мало данных`. */
@Composable
private fun Scales(scales: List<SummaryScale>) {
    val byKind = scales.associateBy(SummaryScale::kind)
    Column(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.content).testTag(TeacherReviewTestTags.SUMMARY_SCALES)) {
        SummaryScaleKind.entries.forEach { kind -> ScaleRow(kind, byKind[kind]) }
    }
}

@Composable
private fun ScaleRow(kind: SummaryScaleKind, scale: SummaryScale?) {
    val value = scale?.value ?: SummaryScaleValue.NOT_ENOUGH_DATA
    val known = value != SummaryScaleValue.NOT_ENOUGH_DATA
    val name = stringResource(kind.label())
    val valueText = stringResource(value.label(kind))
    val reason = scale?.reason?.takeIf { known }
    val line = stringResource(Res.string.teacher_summary_scale_description, name, valueText)
    val description = listOfNotNull(line, reason).joinToString(". ")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ScalePadding)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                name,
                Modifier.weight(1f).padding(end = ItmoTheme.spacing.compact),
                style = ItmoTheme.typography.bodyMedium,
                color = ItmoTheme.colorScheme.onSurface,
            )
            Text(
                valueText,
                style = ItmoTheme.typography.labelLarge,
                color = if (known) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (reason != null) {
            Text(
                reason,
                Modifier.fillMaxWidth(),
                style = ItmoTheme.typography.bodySmall,
                color = ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * `Подробнее` or `Свернуть` with a chevron: a 48 dp text button whose target reaches into the card padding, so the
 * label keeps the content edge and the card ends 4 dp under the target.
 */
@Composable
private fun ScalesToggle(expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) Res.string.teacher_summary_expanded else Res.string.teacher_summary_collapsed)
    val sideInset = ItmoTheme.spacing.cardPadding - ToggleHorizontalPadding
    Row(
        Modifier
            .padding(start = sideInset, bottom = sideInset)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .clip(ItmoTheme.shapes.full)
            .clickable(onClick = onToggle)
            .semantics {
                role = Role.Button
                stateDescription = state
            }
            .testTag(TeacherReviewTestTags.SUMMARY_TOGGLE)
            .padding(horizontal = ToggleHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (expanded) Res.string.teacher_summary_less else Res.string.teacher_summary_more),
            style = ItmoTheme.typography.labelLarge,
            color = ItmoTheme.colorScheme.primary,
        )
        Icon(
            painterResource(if (expanded) KitRes.drawable.ic_summary_collapse else KitRes.drawable.ic_summary_expand),
            contentDescription = null,
            modifier = Modifier.padding(start = ToggleIconPadding).size(ToggleIconSize),
            tint = ItmoTheme.colorScheme.primary,
        )
    }
}

/** DS-01a's `teacher_level_*` token of the tone. */
@Composable
private fun TeacherLevel.color(): Color {
    val colors = ItmoTheme.extendedColors
    return when (this) {
        TeacherLevel.VERY_NEGATIVE -> colors.teacherLevelVeryNegative
        TeacherLevel.NEGATIVE -> colors.teacherLevelNegative
        TeacherLevel.MIXED -> colors.teacherLevelMixed
        TeacherLevel.POSITIVE -> colors.teacherLevelPositive
        TeacherLevel.VERY_POSITIVE -> colors.teacherLevelVeryPositive
    }
}

/** The header's `ic_auto_awesome`. */
private val HeaderIconSize = 20.dp

/** The dot's 5 dp margins: a 20 dp column under the header icon. */
private val DotMargin = 5.dp

/** A point's icon column and its 1 dp vertical padding. */
private val PointIconSize = 16.dp
private val PointPadding = 1.dp
private val PointGlyphSize = 24.dp

/** A scale row's 2 dp vertical padding. */
private val ScalePadding = 2.dp

/** `item_summary_tag_chip.xml`: 28 dp high, 16 dp to the text, a 1 dp `outlineVariant` stroke, 6 dp apart. */
private val TagHeight = 28.dp
private val TagPadding = 16.dp
private val TagStroke = 1.dp
private val TagSpacing = 6.dp

/** The text button's 12 dp sides, 4 dp to its 18 dp chevron. */
private val ToggleHorizontalPadding = 12.dp
private val ToggleIconPadding = 4.dp
private val ToggleIconSize = 18.dp
