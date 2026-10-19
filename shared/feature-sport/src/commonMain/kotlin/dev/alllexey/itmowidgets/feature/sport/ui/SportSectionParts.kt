package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionDateState
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportSectionCardUi
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportSectionDateUi
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_hourglass_top
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import org.jetbrains.compose.resources.painterResource

/**
 * `Секции` of `Моё` (DECISIONS.md, Спорт, «Секция»): each section as a group. Its head row is the sport with the slot,
 * the teacher and the hall as icon lines, the place in the section queue as a pill and the off switch; then its next
 * dates, each a row with its status pill and its own switch; one short note under the list.
 */
internal fun LazyListScope.sections(sections: List<SportSectionCardUi>, actions: SportMyActions) {
    if (sections.isEmpty()) return
    item(key = "sections-title") {
        SectionHeader(text(SportStrings.sport_sections_title), Modifier.padding(top = ItmoTheme.spacing.related).testTag(SportTestTags.SECTIONS))
    }
    items(sections, key = { "section-${it.id}" }) { SectionGroup(it, actions) }
    item(key = "sections-note") { ListGroupNote(text(SportStrings.sport_section_note)) }
}

@Composable
private fun SectionGroup(section: SportSectionCardUi, actions: SportMyActions) {
    val off = text(SportStrings.sport_section_off)
    ListGroup(Modifier.testTag(SportTestTags.section(section.id))) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(ItmoTheme.spacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                    Text(section.title, style = ItmoTheme.typography.titleMedium)
                    MetaLine(painterResource(Res.drawable.ic_schedule), section.slot.asString())
                    section.teacher?.let { MetaLine(painterResource(Res.drawable.ic_person), it) }
                    section.place?.let { MetaLine(painterResource(Res.drawable.ic_location_on), it.asString()) }
                    StatusPill(section.position.asString(), tone = Tone.Primary, icon = painterResource(Res.drawable.ic_group))
                }
                SectionSwitch(
                    checked = true,
                    onCheckedChange = { actions.onStopSection(section.id) },
                    enabled = !section.busy,
                    modifier = Modifier.semantics { contentDescription = off },
                )
            }
        }
        section.dates.forEachIndexed { index, date ->
            item { DateRow(date, { actions.onSectionDate(section.id, index, it) }, Modifier.testTag(SportTestTags.sectionDate(section.id, index))) }
        }
    }
}

/** A secondary line with its small icon: the slot, the teacher, the hall. */
@Composable
private fun MetaLine(icon: Painter, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        Icon(icon, contentDescription = null, Modifier.size(SportSizes.smallIcon), tint = ItmoTheme.colorScheme.onSurfaceVariant)
        Text(text, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One date of a section: the day, its status pill after it (under it when the row is narrow or the font large) and
 * the switch; the whole row toggles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateRow(date: SportSectionDateUi, onCheckedChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(date.on, enabled = date.enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = SportSizes.groupRow)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date.day.asString(), style = ItmoTheme.typography.bodyLarge, softWrap = false)
            StatusPill(
                date.status.asString(),
                tone = date.state.tone(),
                icon = if (date.state == SportSectionDateState.QUEUED) painterResource(Res.drawable.ic_hourglass_top) else null,
            )
        }
        SectionSwitch(date.on, null, date.enabled)
    }
}

/** The queue's warning tone, as on a booking's `очередь 2 из 5`; a spent limit is an error; the rest is quiet. */
private fun SportSectionDateState?.tone(): Tone = when (this) {
    SportSectionDateState.QUEUED -> Tone.Warning
    SportSectionDateState.LIMIT -> Tone.Error
    SportSectionDateState.SKIPPED, null -> Tone.Neutral
}

/** The kit's switch look, a check in the thumb when on, as `SwitchListRow` draws it. */
@Composable
private fun SectionSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}
