package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SmallButton
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportSectionCardUi
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportSectionToggleUi
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_repeat
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import org.jetbrains.compose.resources.painterResource

/**
 * `Секции` of `Моё` (DECISIONS.md, Спорт, «Секция»): each section as a group, the slot with its off switch, the place in
 * the section queue, then its next dates, each with its own switch; the note on how a section queues under the list.
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
            ListRow(
                title = section.title,
                subtitle = section.slot.asString(),
                icon = painterResource(Res.drawable.ic_event_repeat),
                iconTone = Tone.Primary,
                below = {
                    section.venue?.let { Text(it, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium) }
                    Text(section.place.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                },
                accessory = {
                    Switch(
                        checked = true,
                        onCheckedChange = { actions.onStopSection(section.id) },
                        modifier = Modifier.semantics { contentDescription = off },
                        enabled = !section.busy,
                    )
                },
            )
        }
        section.dates.forEachIndexed { index, date ->
            item {
                SwitchListRow(
                    title = date.day.asString(),
                    checked = date.on,
                    onCheckedChange = { actions.onSectionDate(section.id, index, it) },
                    modifier = Modifier.testTag(SportTestTags.sectionDate(section.id, index)),
                    subtitle = date.status.asString(),
                    enabled = date.enabled,
                )
            }
        }
    }
}

/** Under a booked lesson's card: `Сделать секцией`, or the pill `Секция` with the place; a full slot says so. */
@Composable
internal fun BookingSection(section: SportSectionToggleUi, onToggle: (Long) -> Unit) {
    Row(
        Modifier.padding(start = ItmoTheme.spacing.related).testTag(SportTestTags.bookingSection(section.lessonId)),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            section.on -> StatusPill(section.label.asString(), tone = Tone.Primary)
            section.enabled -> SmallButton(section.label.asString(), { onToggle(section.lessonId) })
        }
        section.detail?.let {
            Text(it.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        }
    }
}
