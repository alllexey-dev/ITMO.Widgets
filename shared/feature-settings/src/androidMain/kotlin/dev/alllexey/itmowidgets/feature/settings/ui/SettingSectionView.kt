package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import org.jetbrains.compose.resources.painterResource

/** Tags of the rows' parts for host and instrumented tests; every row is tagged with its `SettingRowId.key`. */
object SettingSectionTags {
    const val FOOTER = "setting_section_footer"
}

/**
 * One [SettingSection] on the kit, as every settings page and the widgets gallery draw it: the [SectionHeader], a
 * lone choice of two to four options as a [SegmentedChoice], any other rows in one [ListGroup], the footer as a
 * [ListGroupNote]. Placed in a column that spaces its children.
 */
@Composable
internal fun SettingSectionView(section: SettingSection, actions: SettingsActions) {
    val choice = section.items.singleOrNull() as? SettingItem.Choice
    section.title?.let { SectionHeader(it.asString()) }
    if (choice != null && choice.options.size <= SEGMENTS) {
        val keys = choice.options.map { it.key }
        SegmentedChoice(
            options = choice.options.map { it.label.asString() },
            selectedIndex = keys.indexOf(choice.selectedOptionKey).coerceAtLeast(0),
            onSelect = { actions.onChoice(choice.id, keys[it]) },
            enabled = choice.enabled && choice.selectedOptionKey != null,
            testTags = keys.map { "${choice.id.key}_$it" },
        )
    } else {
        ListGroup {
            section.items.forEach { setting -> item { SettingRow(setting, actions) } }
        }
    }
    section.footer?.let { ListGroupNote(it.asString(), Modifier.testTag(SettingSectionTags.FOOTER)) }
}

/**
 * A row in a neutral icon tile, like the profile's. A value sits under the title, or at the end when the row has a
 * description; a row that opens a screen ends in a chevron, one that opens the browser in `open_in_new`.
 */
@Composable
private fun SettingRow(item: SettingItem, actions: SettingsActions) {
    val icon = item.icon?.let { painterResource(it.drawable) }
    val tone = Tone.Neutral.takeIf { icon != null }
    val tag = Modifier.testTag(item.id.key)
    when (item) {
        is SettingItem.Toggle -> SwitchListRow(
            title = item.title.asString(),
            checked = item.checked,
            onCheckedChange = { actions.onToggle(item.id, it) },
            modifier = tag,
            subtitle = item.description?.asString(),
            icon = icon,
            iconTone = tone,
            enabled = item.enabled,
        )
        is SettingItem.Choice -> ListRow(
            title = item.title.asString(),
            modifier = tag.disabled(!item.enabled),
            subtitle = item.description?.asString(),
            icon = icon,
            iconTone = tone,
            below = {
                item.options.forEach { option ->
                    val selected = option.key == item.selectedOptionKey
                    ListRow(
                        title = option.label.asString(),
                        // A single choice: TalkBack reads a radio button and its selection.
                        modifier = Modifier.testTag("${item.id.key}_${option.key}").semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        },
                        accessory = if (selected) {
                            { Icon(painterResource(Res.drawable.ic_check), null, tint = ItmoTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = if (item.enabled) ({ actions.onChoice(item.id, option.key) }) else null,
                    )
                }
            },
        )
        is SettingItem.Navigation -> ListRow(
            title = item.title.asString(),
            modifier = tag.disabled(!item.enabled),
            subtitle = (item.description ?: item.value)?.asString(),
            icon = icon,
            iconTone = tone,
            trailing = item.value?.takeIf { item.description != null }?.asString(),
            chevron = true,
            onClick = if (item.enabled) ({ actions.onNavigate(item.page) }) else null,
        )
        is SettingItem.Action -> ListRow(
            title = item.title.asString(),
            modifier = tag.disabled(!item.enabled),
            subtitle = (item.description ?: item.value)?.asString(),
            icon = icon,
            iconTone = tone,
            trailing = item.value?.takeIf { item.description != null }?.asString(),
            accessory = item.trailingIcon?.takeIf { it != AppIcon.CHEVRON_RIGHT }?.let { trailing ->
                {
                    Icon(
                        painterResource(trailing.drawable),
                        contentDescription = null,
                        tint = ItmoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            chevron = item.trailingIcon == AppIcon.CHEVRON_RIGHT,
            onClick = if (item.enabled) ({ actions.onAction(item.id) }) else null,
        )
        is SettingItem.Info -> ListRow(
            title = item.title.asString(),
            modifier = tag,
            subtitle = item.value.asString(),
            icon = icon,
            iconTone = tone,
        )
    }
}

/** A row that cannot act now reads as one: dimmed as the kit's disabled switch row. */
private fun Modifier.disabled(disabled: Boolean): Modifier = if (disabled) alpha(DISABLED_ALPHA) else this

/** More options than this go as a list under the title. */
private const val SEGMENTS = 4
private const val DISABLED_ALPHA = 0.38f
