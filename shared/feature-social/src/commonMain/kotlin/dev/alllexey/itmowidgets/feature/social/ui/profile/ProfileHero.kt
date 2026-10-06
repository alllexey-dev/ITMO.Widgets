package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.designsystem.components.buttons.ButtonRow
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoHeroAvatar
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileHeadline
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.reviews.overlap
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.person_course
import dev.alllexey.itmowidgets.shared.feature.social.person_isu_copy
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_accept_request
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_add_friend
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_cancel_request
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_friend_badge
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_reject_request
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_self
import dev.alllexey.itmowidgets.shared.feature.social.user_status_incoming
import dev.alllexey.itmowidgets.shared.feature.social.user_status_outgoing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * The hero card (`item_profile_header.xml`, `Card.Hero`): the avatar with initials when there is no photo or it fails,
 * the name, one short line, the ISU number with a copy symbol and, with a social block, the friendship.
 */
@Composable
internal fun ProfileHero(state: UserProfileUiState.Content, actions: UserProfileActions) {
    val name = state.displayName.asString()
    Column(
        Modifier
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .clip(ItmoTheme.shapes.cardHero)
            .background(ItmoTheme.colorScheme.surfaceContainer)
            .padding(ItmoTheme.spacing.summaryPadding)
            .testTag(UserProfileTestTags.HERO),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItmoHeroAvatar(name, state.pictureUrl)
            Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.group)) {
                Text(
                    name,
                    Modifier.fillMaxWidth().semantics { heading() },
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.titleLarge,
                )
                state.headline?.text()?.let { line ->
                    Text(
                        line,
                        Modifier.fillMaxWidth().padding(top = HeadlineGap),
                        color = ItmoTheme.colorScheme.onSurfaceVariant,
                        style = ItmoTheme.typography.bodyMedium,
                    )
                }
                IsuLine(state.isu) { actions.onCopyIsu(state.isu) }
                state.social?.badge()?.let { badge ->
                    Pill(
                        stringResource(badge),
                        Modifier.padding(top = ItmoTheme.spacing.compact).testTag(UserProfileTestTags.BADGE),
                    )
                }
            }
        }
        state.social?.let { Friendship(it, actions) }
    }
}

/**
 * The ISU number alone with a 16 dp copy symbol: a 48 dp target drawn into the gaps around the line, so the card
 * keeps its compact rhythm. TalkBack reads `Номер ИСУ N, скопировать`.
 */
@Composable
private fun IsuLine(isu: Int, onCopy: () -> Unit) {
    val description = stringResource(Res.string.person_isu_copy, isu)
    // The target is a node of its own inside the overlap, so its bounds keep the full 48 dp.
    Box(Modifier.overlap(top = IsuOverlap, bottom = IsuOverlap)) { IsuTarget(isu, description, onCopy) }
}

@Composable
private fun IsuTarget(isu: Int, description: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onCopy)
            .semantics { contentDescription = description }
            .padding(end = ItmoTheme.spacing.content)
            .testTag(UserProfileTestTags.ISU),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            isu.toString(),
            Modifier.clearAndSetSemantics {},
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        Icon(
            painterResource(KitRes.drawable.ic_content_copy),
            contentDescription = null,
            modifier = Modifier.padding(start = CopyIconGap).size(CopyIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The friendship under the person: an open request says so above its buttons; anyone else without a badge gets
 * `Добавить в друзья`. Nothing for the viewer, a friend (both have the badge), a blocked person or a state this
 * version does not know. The buttons ignore taps while a request is in flight.
 */
@Composable
private fun Friendship(social: SocialBlock, actions: UserProfileActions) {
    val buttons = social.buttons() ?: return
    val status = social.status()
    if (status != null) {
        Text(
            stringResource(status),
            Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.group),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
    }
    // A status line already separates the buttons from the person.
    val top = if (status != null) ItmoTheme.spacing.compact else ItmoTheme.spacing.group
    ButtonRow(Modifier.fillMaxWidth().padding(top = top)) {
        ProgressButton(
            stringResource(buttons.primary),
            onClick = actions.onPrimaryAction,
            modifier = Modifier.testTag(UserProfileTestTags.PRIMARY),
            style = buttons.primaryStyle,
            enabled = !social.busy,
        )
        if (buttons.secondary != null) {
            ProgressButton(
                stringResource(buttons.secondary),
                onClick = actions.onSecondaryAction,
                modifier = Modifier.testTag(UserProfileTestTags.SECONDARY),
                style = ProgressButtonStyle.Tonal,
                enabled = !social.busy,
            )
        }
    }
}

/** The friendship's buttons: filled for joining, tonal for stepping back. */
private class FriendshipButtons(
    val primary: StringResource,
    val primaryStyle: ProgressButtonStyle,
    val secondary: StringResource? = null,
)

private fun SocialBlock.buttons(): FriendshipButtons? {
    if (isSelf) return null
    return when (profile.relationship) {
        RelationshipState.NONE -> FriendshipButtons(Res.string.user_profile_add_friend, ProgressButtonStyle.Filled)
        RelationshipState.OUTGOING -> FriendshipButtons(Res.string.user_profile_cancel_request, ProgressButtonStyle.Tonal)
        RelationshipState.INCOMING -> FriendshipButtons(
            Res.string.user_profile_accept_request,
            ProgressButtonStyle.Filled,
            Res.string.user_profile_reject_request,
        )
        RelationshipState.FRIENDS, RelationshipState.BLOCKED -> null
    }
}

private fun SocialBlock.status(): StringResource? = when {
    isSelf -> null
    profile.relationship == RelationshipState.INCOMING -> Res.string.user_status_incoming
    profile.relationship == RelationshipState.OUTGOING -> Res.string.user_status_outgoing
    else -> null
}

/** `это вы` for the viewer, `в друзьях` for a friend. */
private fun SocialBlock.badge(): StringResource? = when {
    isSelf -> Res.string.user_profile_self
    profile.relationship == RelationshipState.FRIENDS -> Res.string.user_profile_friend_badge
    else -> null
}

/** «Преподаватель» or «M3234, 2 курс»; null when there is nothing to say. */
@Composable
private fun ProfileHeadline.text(): String? = when (this) {
    is ProfileHeadline.Position -> role
    is ProfileHeadline.Group -> listOfNotNull(name, course?.let { stringResource(Res.string.person_course, it) })
        .joinToString(", ")
}.ifEmpty { null }

/** `item_profile_header.xml`'s 2 dp between the name and the headline. */
private val HeadlineGap = 2.dp

/** The ISU line's -12 dp margins: its 48 dp target reaches into the gaps around it. */
private val IsuOverlap = 12.dp

private val CopyIconSize = 16.dp
private val CopyIconGap = 6.dp

/** The ISU number keeps its digits aligned (`fontFeatureSettings="tnum"`). */
private const val TABULAR_FIGURES = "tnum"
