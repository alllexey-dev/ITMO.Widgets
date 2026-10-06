package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/** The space above a [SectionHeading], by where the section stands. */
enum class SectionHeadingSpacing {
    /** A section below another one on a page: 24 dp. */
    Section,

    /** A section inside a bottom sheet: 16 dp. */
    Sheet,

    /** The first section of a page or sheet: 8 dp. */
    First,
}

/**
 * The heading above a connected group (`item_section_heading.xml`): `titleSmall` in `primary`, 16 dp in from the
 * group's edge, in line with the rows' content, [spacing] above and 8 dp below.
 *
 * Under the iOS style it is an inset group's section header: semibold body size (`titleMedium`, Apple's headline) in
 * `secondaryLabel`, in line with the rows' text, at UIKit's header insets.
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    spacing: SectionHeadingSpacing = SectionHeadingSpacing.Section,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        val top = if (spacing == SectionHeadingSpacing.First) ItmoTheme.spacing.compact else IosMetrics.sectionHeaderTop
        IosSectionHeader(text, modifier, top)
        return
    }
    Text(
        text,
        modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.cardPadding,
                top = spacing.top(),
                end = ItmoTheme.spacing.cardPadding,
                bottom = ItmoTheme.spacing.compact,
            )
            .semantics { heading() },
        color = ItmoTheme.colorScheme.primary,
        style = ItmoTheme.typography.titleSmall,
    )
}

/**
 * A sub-heading inside a section (`item_recordbook_control_group.xml`): [title] in `titleSmall` on `onSurface` with
 * an optional [value] at the end, such as a control group's `30 / 48`, and an optional [supporting] line below the
 * title. It starts a group of its own and is read as one heading. Under the iOS style the title and the value take
 * the section header's `secondaryLabel` and its distance to the group.
 */
@Composable
fun SectionSubheading(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: (@Composable () -> Unit)? = null,
) {
    val ios = ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
    val titleColor = if (ios) ItmoTheme.iosColors.secondaryLabel else ItmoTheme.colorScheme.onSurface
    val valueColor = if (ios) ItmoTheme.iosColors.secondaryLabel else ItmoTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .semantics(mergeDescendants = true) { heading() }
            .padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.content,
                end = ItmoTheme.spacing.cardPadding,
                bottom = if (ios) IosMetrics.sectionHeaderBottom else ItmoTheme.spacing.compact,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, style = ItmoTheme.typography.titleSmall)
            if (supporting != null) {
                Column(Modifier.padding(top = ItmoTheme.spacing.related)) { supporting() }
            }
        }
        if (value != null) {
            Text(
                value,
                Modifier.padding(start = ItmoTheme.spacing.compact),
                color = valueColor,
                style = ItmoTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/**
 * An inset group's section header (`UIListContentConfiguration.groupedHeader()`): semibold 17 pt in
 * `secondaryLabel`, inset to the rows' text, [top] above and UIKit's header bottom below; also the title of a
 * `SettingsGroup` under the iOS style.
 */
@Composable
internal fun IosSectionHeader(text: String, modifier: Modifier, top: Dp) {
    Text(
        text,
        modifier
            .fillMaxWidth()
            .padding(
                start = IosMetrics.rowHorizontalPadding,
                top = top,
                end = IosMetrics.rowHorizontalPadding,
                bottom = IosMetrics.sectionHeaderBottom,
            )
            .semantics { heading() },
        color = ItmoTheme.iosColors.secondaryLabel,
        style = ItmoTheme.typography.titleMedium,
    )
}

@Composable
private fun SectionHeadingSpacing.top(): Dp = when (this) {
    SectionHeadingSpacing.Section -> ItmoTheme.spacing.section
    SectionHeadingSpacing.Sheet -> ItmoTheme.spacing.group
    SectionHeadingSpacing.First -> ItmoTheme.spacing.compact
}

/** The control group's score keeps its digits aligned as it changes (`fontFeatureSettings="tnum"`). */
private const val TABULAR_FIGURES = "tnum"
