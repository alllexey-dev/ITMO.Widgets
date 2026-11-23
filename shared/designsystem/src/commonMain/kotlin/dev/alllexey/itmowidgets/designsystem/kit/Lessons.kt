package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import org.jetbrains.compose.resources.painterResource

/** Where a lesson stands in its day. */
enum class LessonState { Upcoming, Next, Now, Done }

/**
 * What every lesson view says, in one order: [subject]; the [type] in its colour and the [people] (`Лекция ·
 * Белов П. И.`); [place] `аудитория, корпус` beside a pin (null online: an `онлайн` pill says it); the lesson's
 * [note]; the [pills] (`идёт`, `далее`, `изменена`, `отменена`,
 * `очередь 2 из 5`, `записаны`, `онлайн`). [previousPlace] is the old room or time of a change, struck through before
 * the new one; [friends] are the people who attend as avatar letters (`ИК`, `Вы`). [accent] is the lesson type's colour. Texts arrive
 * formatted: the kit draws, the shared presentation decides.
 */
@Immutable
data class LessonView(
    val start: String,
    val end: String,
    val subject: String,
    val type: String,
    val people: String?,
    val place: String?,
    val accent: Color,
    val state: LessonState = LessonState.Upcoming,
    val previousPlace: String? = null,
    val cancelled: Boolean = false,
    val note: String? = null,
    val pills: List<PillSpec> = emptyList(),
    val friends: List<String> = emptyList(),
)

/**
 * A single lesson outside a day list (the neighbour of a hero, a sport booking, a stale preview): the time column, a
 * bar in the type's colour and the lesson's lines. The current lesson sits on `primaryContainer`; a done one is
 * dimmed. With [onClick] the whole card opens the lesson. A day's lessons are a [LessonTimeline], never a stack of
 * these.
 */
@Composable
fun LessonCard(lesson: LessonView, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val now = lesson.state == LessonState.Now
    val dimmed = lesson.state == LessonState.Done && !lesson.cancelled
    Surface(
        modifier = modifier.fillMaxWidth().alpha(if (dimmed) DONE_ALPHA else 1f)
            .then(if (onClick != null) Modifier.clip(ItmoTheme.shapes.cardContent).clickable(onClick = onClick) else Modifier),
        shape = ItmoTheme.shapes.cardContent,
        color = if (now) ItmoTheme.colorScheme.primaryContainer else cardSurface(),
        contentColor = if (now) ItmoTheme.colorScheme.onPrimaryContainer else ItmoTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.height(IntrinsicSize.Min).padding(ItmoTheme.spacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Column(Modifier.widthIn(min = CardTimeColumn)) {
                Text(
                    lesson.start,
                    style = ItmoTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = false,
                )
                Text(lesson.end, color = secondary(now), style = ItmoTheme.typography.bodySmall, softWrap = false)
            }
            Box(Modifier.width(TypeBarWidth).fillMaxHeight().background(lesson.accent, ItmoTheme.shapes.extraSmall))
            LessonLines(lesson, Modifier.weight(1f))
        }
    }
}

/**
 * The lines of a lesson in the order every lesson view uses; shared by [LessonCard] and [LessonTimeline].
 * [onContainer]: the lines sit on `primaryContainer` (the current lesson of a [LessonCard]), where the type keeps the
 * content colour.
 */
@Composable
internal fun LessonLines(
    lesson: LessonView,
    modifier: Modifier = Modifier,
    onContainer: Boolean = lesson.state == LessonState.Now,
) {
    val content = LocalContentColor.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LineGap)) {
        Text(
            lesson.subject,
            style = ItmoTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null,
        )
        // The type's colour is the dot's, pulled towards the text colour until it reads as text.
        val typeColor = if (onContainer) content else lerp(lesson.accent, content, TYPE_TEXT_BLEND)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.Medium)) { append(lesson.type) }
                if (lesson.people != null) append("$PEOPLE_SEPARATOR${lesson.people}")
            },
            color = secondary(onContainer),
            style = ItmoTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lesson.place != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlaceGap),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_location_on),
                    contentDescription = null,
                    Modifier.size(PinSize),
                    tint = secondary(onContainer),
                )
                if (lesson.previousPlace != null) {
                    Text(
                        lesson.previousPlace,
                        color = secondary(onContainer),
                        style = ItmoTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
                Text(
                    lesson.place,
                    color = if (lesson.previousPlace != null) ItmoTheme.extendedColors.warning else content,
                    style = ItmoTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        if (lesson.note != null) {
            Text(
                lesson.note,
                color = secondary(onContainer),
                style = ItmoTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (lesson.pills.isNotEmpty() || lesson.friends.isNotEmpty()) {
            Row(
                Modifier.padding(top = ItmoTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlaceGap),
            ) {
                lesson.pills.forEach { StatusPill(it) }
                if (lesson.friends.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(-FriendOverlap)) {
                        lesson.friends.take(MAX_FRIENDS).forEach { PersonLetters(it) }
                    }
                }
            }
        }
    }
}

/** A small round badge with a person's letters, as the lesson lines show who attends. */
@Composable
private fun PersonLetters(letters: String) {
    Box(
        Modifier.size(FriendSize).background(ItmoTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letters,
            color = ItmoTheme.colorScheme.onTertiaryContainer,
            style = ItmoTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun secondary(onPrimaryContainer: Boolean): Color = if (onPrimaryContainer) {
    ItmoTheme.colorScheme.onPrimaryContainer.copy(alpha = ON_PRIMARY_SECONDARY_ALPHA)
} else {
    ItmoTheme.colorScheme.onSurfaceVariant
}

/** The opacity of a done lesson, in a card or on the timeline. */
internal const val DONE_ALPHA = 0.6f
private const val ON_PRIMARY_SECONDARY_ALPHA = 0.75f
private const val MAX_FRIENDS = 3
private const val TYPE_TEXT_BLEND = 0.4f
private const val PEOPLE_SEPARATOR = " · "
private val PinSize = 16.dp

private val CardTimeColumn = 44.dp
private val TypeBarWidth = 4.dp
private val LineGap = 2.dp
private val PlaceGap = 6.dp
private val FriendSize = 24.dp
private val FriendOverlap = 6.dp
