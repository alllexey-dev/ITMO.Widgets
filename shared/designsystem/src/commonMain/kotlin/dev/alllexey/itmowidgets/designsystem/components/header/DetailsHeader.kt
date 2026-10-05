package dev.alllexey.itmowidgets.designsystem.components.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.buttons.ToneDot
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import org.jetbrains.compose.resources.painterResource

/** A fact row of a [DetailsHeader]: [value] on screen, [label] (`Место`) only for TalkBack, where the icon says it. */
@Immutable
data class DetailsFact(val label: String, val value: String)

/**
 * The teacher row of a [DetailsHeader]. [onClick] only for a teacher with an id: the row becomes a target with a
 * chevron and [clickLabel] as its TalkBack action. [tone] is the review tone dot before the chevron, described by
 * [toneDescription]; [reserveTone] keeps the dot's place while the tone is loading, so a late tone does not move the
 * name.
 */
@Immutable
data class DetailsTeacher(
    val fact: DetailsFact,
    val onClick: (() -> Unit)? = null,
    val clickLabel: String? = null,
    val tone: Color? = null,
    val toneDescription: String? = null,
    val reserveTone: Boolean = false,
)

/** The map button under the place: [label] and what it opens. */
@Immutable
data class DetailsMapAction(val label: String, val onClick: () -> Unit)

/**
 * The head of every details sheet (port of `core/ui/DetailsHeader.kt` and `view_details_header.xml`): what ([title],
 * [kind] with an optional type colour), when ([date], [time] and [duration], all preformatted), who and where
 * ([teacher], [flow], [place]) and the [map] button. A missing row disappears instead of reading as empty. The caller
 * pads the header horizontally, as the sheets pad their content.
 */
@Composable
fun DetailsHeader(
    title: String,
    date: String,
    time: String,
    modifier: Modifier = Modifier,
    kind: String? = null,
    kindColor: Color? = null,
    duration: String? = null,
    teacher: DetailsTeacher? = null,
    flow: DetailsFact? = null,
    place: DetailsFact? = null,
    map: DetailsMapAction? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            Modifier
                .padding(top = ItmoTheme.spacing.compact)
                .semantics { heading() },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleLarge,
        )
        if (!kind.isNullOrBlank()) KindRow(kind, kindColor)
        TimeRow(date, time, duration)
        val facts = listOfNotNull(teacher?.fact, flow, place).filter { it.value.isNotBlank() }
        if (facts.isNotEmpty() || map != null) {
            Column(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                if (teacher != null && teacher.fact.value.isNotBlank()) TeacherRow(teacher)
                flow?.takeIf { it.value.isNotBlank() }?.let { FactRow(it, painterResource(Res.drawable.ic_group)) }
                place?.takeIf { it.value.isNotBlank() }?.let { FactRow(it, painterResource(Res.drawable.ic_location_on)) }
                if (map != null) MapButton(map)
            }
        }
    }
}

@Composable
private fun KindRow(kind: String, kindColor: Color?) {
    Row(Modifier.padding(top = ItmoTheme.spacing.related), verticalAlignment = Alignment.CenterVertically) {
        if (kindColor != null) {
            ToneDot(kindColor)
            Spacer(Modifier.width(ItmoTheme.spacing.compact))
        }
        Text(kind, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeRow(date: String, time: String, duration: String?) {
    val dateStyle = ItmoTheme.typography.bodyMedium
    Row(Modifier.padding(top = ItmoTheme.spacing.compact).semantics(mergeDescendants = true) {}) {
        RailIcon(painterResource(Res.drawable.ic_schedule), dateStyle)
        Column(Modifier.weight(1f)) {
            Text(date, color = ItmoTheme.colorScheme.onSurfaceVariant, style = dateStyle)
            Row(Modifier.padding(top = TimeGap), verticalAlignment = Alignment.Bottom) {
                Text(time, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
                if (duration != null) {
                    Text(
                        duration,
                        Modifier
                            .weight(1f)
                            .padding(start = ItmoTheme.spacing.content),
                        color = ItmoTheme.colorScheme.onSurfaceVariant,
                        style = ItmoTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherRow(teacher: DetailsTeacher) {
    val onClick = teacher.onClick
    val interaction = if (onClick == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(onClickLabel = teacher.clickLabel, onClick = onClick)
    }
    FactRow(teacher.fact, painterResource(Res.drawable.ic_person), interaction, clickable = onClick != null) {
        if (teacher.tone != null || teacher.reserveTone) {
            val description = teacher.toneDescription
            ToneDot(
                teacher.tone,
                Modifier
                    .padding(start = ItmoTheme.spacing.compact)
                    .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
            )
        }
        if (onClick != null) {
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = ItmoTheme.spacing.compact)
                    .size(RailIconSize),
                tint = ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * `item_sport_detail_fact.xml`: a 20 dp rail icon on the first line, the value, optional trailing marks. A clickable
 * row is one touch target high, its text centred in it.
 */
@Composable
private fun FactRow(
    fact: DetailsFact,
    icon: Painter,
    interaction: Modifier = Modifier.semantics(mergeDescendants = true) {},
    clickable: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val style = ItmoTheme.typography.bodyMedium
    val minimum = ItmoTheme.spacing.compact
    val touchTarget = ItmoTheme.spacing.touchTarget
    val vertical = if (clickable) ((touchTarget - style.lineHeightDp()) / 2).coerceAtLeast(minimum) else minimum
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (clickable) touchTarget else 0.dp)
            .then(interaction)
            .padding(vertical = vertical),
    ) {
        RailIcon(icon, style, label = fact.label)
        Text(fact.value, Modifier.weight(1f), color = ItmoTheme.colorScheme.onSurfaceVariant, style = style)
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** A 20 dp icon centred on the first line of text in [style], at any font scale (`alignRailIcon`). */
@Composable
private fun RailIcon(icon: Painter, style: TextStyle, label: String? = null) {
    val top = ((style.lineHeightDp() - RailIconSize) / 2).coerceAtLeast(0.dp)
    Icon(
        icon,
        contentDescription = label,
        modifier = Modifier
            .padding(top = top, end = ItmoTheme.spacing.compact)
            .size(RailIconSize),
        tint = ItmoTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MapButton(map: DetailsMapAction) {
    ElevatedButton(onClick = map.onClick, modifier = Modifier.padding(start = MapButtonInset)) {
        Icon(painterResource(Res.drawable.ic_location_on), contentDescription = null, Modifier.size(MapIconSize))
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        Text(map.label)
    }
}

@Composable
private fun TextStyle.lineHeightDp(): Dp = with(LocalDensity.current) { lineHeight.toDp() }

/** `item_sport_detail_fact.xml`'s icons and chevron. */
private val RailIconSize = 20.dp

/** `view_details_header.xml`: 2 dp between the date and the time. */
private val TimeGap = 2.dp

/** `view_details_header.xml`'s `map_button` `layout_marginStart`. */
private val MapButtonInset = 16.dp

/** `Widget.Material3.Button`'s `iconSize`. */
private val MapIconSize = 18.dp
