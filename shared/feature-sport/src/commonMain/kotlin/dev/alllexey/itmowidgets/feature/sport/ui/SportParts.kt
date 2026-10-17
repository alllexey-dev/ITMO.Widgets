package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportKindTone
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSeatsTone

// Small pieces the sport screens share on Android; the kit has the rest.

/** A catalog key as text. */
@Composable
internal fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

/** The colour of a lesson kind's dot, from the lesson type colours: free lessons are sport's, sections a lab's. */
@Composable
internal fun SportKindTone.color(): Color = with(ItmoTheme.extendedColors) {
    when (this@color) {
        SportKindTone.FREE -> lessonTypeSport
        SportKindTone.SECTION -> lessonTypeLab
        SportKindTone.TEAM -> lessonTypeAssessment
        SportKindTone.OTHER -> lessonTypeDefault
    }
}

internal fun SportSeatsTone.tone(): Tone = when (this) {
    SportSeatsTone.OK -> Tone.Success
    SportSeatsTone.FEW -> Tone.Warning
    SportSeatsTone.FULL -> Tone.Error
}

/** `• Свободное, Кронва`: the kind's dot and the kind with the place. */
@Composable
internal fun KindTag(kind: String?, tone: SportKindTone, place: String?, modifier: Modifier = Modifier) {
    val label = listOfNotNull(kind, place).joinToString(", ")
    if (label.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
        Box(Modifier.size(ItmoTheme.spacing.compact).background(tone.color(), CircleShape))
        Text(
            label,
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Friends' avatars overlapping, at most three. */
@Composable
internal fun FriendAvatars(friends: List<UserSummary>, modifier: Modifier = Modifier) {
    if (friends.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-ItmoTheme.spacing.compact)) {
        friends.take(MAX_FRIENDS).forEach {
            Avatar(it.name, pictureUrl = it.pictureUrl, size = ItmoTheme.spacing.section, initials = it.initials)
        }
    }
}

/** A fixed column for `08:20` over `09:50` in a row. */
@Composable
internal fun Modifier.timeColumn(): Modifier = width(ItmoTheme.spacing.touchTarget)

private const val MAX_FRIENDS = 3

/** Sizes the sport screens share; spacing comes from the theme. */
internal object SportSizes {
    val ring = 88.dp
    val ringLarge = 128.dp
    val ringThickness = 8.dp
    val ringThicknessLarge = 12.dp
    val smallIcon = 16.dp
    val icon = 20.dp
    val dot = 10.dp
    val checkIcon = 24.dp
    val blockedReason = 112.dp
    /** Below this a catalog row puts its action under its lines. */
    val compactRow = 360.dp
    val seatBar = 12.dp
    val smallButton = PaddingValues(horizontal = 14.dp)
}
