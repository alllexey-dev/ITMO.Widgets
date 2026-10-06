package dev.alllexey.itmowidgets.designsystem.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.groups.IosSectionHeader
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/**
 * How tightly the rows of a settings group sit. Groups carry no outer spacing: a screen spaces them by
 * `ItmoTheme.spacing.group` ([Compact]) or `ItmoTheme.spacing.section` ([Default]).
 */
enum class SettingsDensity {
    /**
     * `Widget.ItmoWidgets.CompactSettingsRow`: rows at least 48 dp high with 8 dp vertical padding, inset half-alpha
     * dividers. Settings, onboarding, Me and profile sharing (`docs/settings.md`).
     */
    Compact,

    /** `Widget.ItmoWidgets.SettingsRow`: rows at least 56 dp high with 16 dp horizontal and 12 dp vertical padding. */
    Default,
}

/** Collects the rows of a [SettingsGroup]; the group draws a divider between every two of them. */
class SettingsGroupScope internal constructor() {
    internal val rows = mutableListOf<SettingsGroupRow>()

    /** Adds a row; a stable [key] keeps its state (a switch mid-animation) when rows before it come and go. */
    fun row(key: Any? = null, content: @Composable () -> Unit) {
        rows += SettingsGroupRow(key, content)
    }
}

internal class SettingsGroupRow(val key: Any?, val content: @Composable () -> Unit)

/**
 * One settings or profile group, never a card per row: an optional [title] label, one card (20 dp corners,
 * `surfaceContainerLow`) with the rows of [content] and dividers between them, and an optional [footer] below, usually
 * [SettingsGroupFooter]. The rows read [density] from the group.
 *
 * Under the iOS style it is a section of an inset-grouped list: the [title] as its header, one inset group of cells
 * with hairline separators inset to the rows' text, the footer below; both densities draw UIKit's rows.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    density: SettingsDensity = SettingsDensity.Compact,
    footer: (@Composable () -> Unit)? = null,
    content: SettingsGroupScope.() -> Unit,
) {
    val rows = SettingsGroupScope().apply(content).rows
    CompositionLocalProvider(LocalSettingsDensity provides density) {
        Column(modifier.fillMaxWidth()) {
            val ios = ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
            val surface = if (ios) ItmoTheme.iosColors.groupedCell else ItmoTheme.colorScheme.surfaceContainerLow
            if (title != null) {
                if (ios) IosSectionHeader(title, Modifier, top = 0.dp) else SettingsGroupLabel(title, density)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(ItmoTheme.shapes.cardContent)
                    .background(surface),
            ) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) SettingsDivider(density)
                    key(row.key ?: index) { row.content() }
                }
            }
            footer?.invoke()
        }
    }
}

/**
 * The note under a settings group (`Widget.ItmoWidgets.CompactSettingsSectionFooter`): `bodySmall`, 8 dp below it.
 * Under the iOS style an inset group's footer (`groupedFooter()`): footnote in `secondaryLabel`, inset to the rows'
 * text, at UIKit's distance below the group.
 */
@Composable
fun SettingsGroupFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        Text(
            text,
            modifier
                .fillMaxWidth()
                .padding(
                    start = IosMetrics.rowHorizontalPadding,
                    top = IosMetrics.sectionFooterTop,
                    end = IosMetrics.rowHorizontalPadding,
                ),
            color = ItmoTheme.iosColors.secondaryLabel,
            style = ItmoTheme.typography.bodySmall,
        )
        return
    }
    Text(
        text,
        modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.group,
                top = ItmoTheme.spacing.compact,
                end = ItmoTheme.spacing.group,
            ),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodySmall,
    )
}

/** `Widget.ItmoWidgets.SettingsGroupLabel`: `labelLarge` on `onSurfaceVariant`, in line with the row titles. */
@Composable
private fun SettingsGroupLabel(text: String, density: SettingsDensity) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.group,
                end = if (density == SettingsDensity.Compact) ItmoTheme.spacing.group else 0.dp,
                bottom = ItmoTheme.spacing.compact,
            )
            .semantics { heading() },
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.labelLarge,
    )
}

/** `Widget.ItmoWidgets.SettingsDivider`; the compact one is inset at both ends and half as strong. */
@Composable
private fun SettingsDivider(density: SettingsDensity) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        // The settings rows start their text at the cell's margin, so every separator takes the plain inset.
        HorizontalDivider(
            Modifier.padding(start = IosMetrics.separatorInset, end = IosMetrics.separatorTrailingInset),
            thickness = IosMetrics.separatorThickness,
            color = ItmoTheme.iosColors.separator,
        )
        return
    }
    val compact = density == SettingsDensity.Compact
    HorizontalDivider(
        Modifier.padding(start = ItmoTheme.spacing.group, end = if (compact) ItmoTheme.spacing.group else 0.dp),
        thickness = DividerThickness,
        color = ItmoTheme.colorScheme.outlineVariant.copy(alpha = if (compact) COMPACT_DIVIDER_ALPHA else 1f),
    )
}

internal val LocalSettingsDensity = staticCompositionLocalOf { SettingsDensity.Compact }

/** `Widget.ItmoWidgets.SettingsDivider`'s 1 dp line. */
private val DividerThickness = 1.dp

/** `Widget.ItmoWidgets.CompactSettingsDivider`'s `android:alpha`. */
private const val COMPACT_DIVIDER_ALPHA = 0.5f
