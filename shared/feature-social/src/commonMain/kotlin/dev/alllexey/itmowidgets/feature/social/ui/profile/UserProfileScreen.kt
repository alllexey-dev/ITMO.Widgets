package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.kit.HeaderAction
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.CardAction
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileReviews
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileRole
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileRoleKind
import dev.alllexey.itmowidgets.feature.social.presentation.StudyBlock
import dev.alllexey.itmowidgets.feature.social.presentation.TeachingBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.reviews.OwnTeacherReviewRow
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherReviewActions
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherReviewRow
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherSummaryCard
import dev.alllexey.itmowidgets.feature.social.ui.text
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_month
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_note
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_remove
import dev.alllexey.itmowidgets.shared.designsystem.ic_school
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_undo
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * What the person card reports, in `UserProfileViewModel`'s terms: [onAction] is a header button, [onRemoveFriend]
 * `Удалить из друзей`, which the ViewModel confirms first. [onFriends], [onSchedule] and [onSport] fire only for open
 * rows. Every effect (share, clipboard, navigation) belongs to the host.
 */
class UserProfileActions(
    val onBack: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCopyIsu: (isu: Int) -> Unit = {},
    val onAction: (CardAction) -> Unit = {},
    val onRemoveFriend: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onSchedule: () -> Unit = {},
    val onSport: () -> Unit = {},
    val onToggleSummary: () -> Unit = {},
    val review: TeacherReviewActions = TeacherReviewActions(),
)

/** Test tags of [UserProfileScreen]; the review rows and the summary carry `TeacherReviewTestTags`. */
object UserProfileTestTags {
    const val LIST = "user_profile_list"
    const val LOADING = "user_profile_loading"

    /** The not-found or error state in the page's place. */
    const val STATE = "user_profile_state"
    const val SHARE = "user_profile_share"
    const val HERO = "user_profile_hero"
    const val ISU = "user_profile_isu"
    const val PRIMARY = "user_profile_primary"
    const val SECONDARY = "user_profile_secondary"
    const val TEACHING = "user_profile_teaching"
    const val NOW = "user_profile_now"
    const val STUDY = "user_profile_study"
    const val REMOVE = "user_profile_remove"
    const val FRIENDS = "user_profile_friends"
    const val SCHEDULE = "user_profile_schedule"
    const val SPORT = "user_profile_sport"
    const val REVIEWS = "user_profile_reviews"
}

/**
 * The person card (DECISIONS.md, Карточка человека), one for any ISU: a skeleton until the parts settle,
 * `Профиль не найден` without an action, `Не удалось загрузить` with the reason and `Повторить`, or the card. The card
 * is a lazy list with stable keys: the centred header (avatar, full name, roles, ISU with copy, up to two full-width
 * buttons, the filled one first), then "Преподавание" (teaches you, where to find, the AI summary and the reviews) and
 * "Учёба" (the groups, then what the viewer may open), in the order the state gives, and `Удалить из друзей` last.
 * Someone else's hidden data is simply absent. Stateless; the route feeds it.
 */
@Composable
fun UserProfileScreen(
    state: UserProfileUiState,
    actions: UserProfileActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // The header carries the name, so the bar shows only back and share, as a contact card does.
    ItmoDetailScreen(
        title = "",
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
        actions = {
            if (state is UserProfileUiState.Content) {
                HeaderAction(
                    painterResource(Res.drawable.ic_share),
                    text(CoreStrings.share_action),
                    onClick = actions.onShare,
                    modifier = Modifier.testTag(UserProfileTestTags.SHARE),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                UserProfileUiState.Loading -> Skeleton(
                    SkeletonStyle.List,
                    Modifier.testTag(UserProfileTestTags.LOADING),
                    rows = SKELETON_ROWS,
                )
                is UserProfileUiState.Error -> ProfileError(state.error, actions.onRetry)
                is UserProfileUiState.Content -> Card(state, actions, listState)
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A missing person has nothing to retry; any other failure says why and offers `Повторить`. */
@Composable
private fun ProfileError(error: AppError, onRetry: () -> Unit) {
    val notFound = error == AppError.NotFound
    ContentState(
        title = text(if (notFound) SocialStrings.user_profile_not_found_title else CoreStrings.common_load_error_title),
        modifier = Modifier.fillMaxSize().testTag(UserProfileTestTags.STATE),
        icon = painterResource(if (notFound) Res.drawable.ic_person else Res.drawable.ic_error),
        description = if (notFound) null else error.toUiText().asString(),
        action = if (notFound) null else ContentStateAction(text(CoreStrings.common_retry), onRetry),
    )
}

@Composable
private fun Card(state: UserProfileUiState.Content, actions: UserProfileActions, listState: LazyListState) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(UserProfileTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "header", contentType = "header") { Header(state, actions) }
        if (state.teachingFirst) {
            teaching(state, actions)
            study(state.study, actions)
        } else {
            study(state.study, actions)
            teaching(state, actions)
        }
        if (state.canRemoveFriend) {
            item(key = "remove", contentType = "remove") {
                ListGroup(Modifier.padding(top = ItmoTheme.spacing.content)) {
                    item {
                        ListRow(
                            text(SocialStrings.user_profile_remove_friend),
                            Modifier.testTag(UserProfileTestTags.REMOVE),
                            icon = painterResource(Res.drawable.ic_remove),
                            iconTone = Tone.Error,
                            danger = true,
                            onClick = actions.onRemoveFriend,
                        )
                    }
                }
            }
        }
    }
}

/** One centred block, as a contact card: avatar, full name, roles, ISU with copy, then the buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: UserProfileUiState.Content, actions: UserProfileActions) {
    val name = state.displayName.asString()
    Column(
        Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact).testTag(UserProfileTestTags.HERO),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(name, state.pictureUrl, size = AvatarSize, initials = state.initials)
        Text(
            name,
            Modifier.padding(top = ItmoTheme.spacing.content).semantics { heading() },
            style = ItmoTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (state.roles.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = ItmoTheme.spacing.compact),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
            ) {
                state.roles.forEach { RolePill(it) }
            }
        }
        IsuLine(state.isu, actions.onCopyIsu)
        if (state.actions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.content),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            ) {
                state.actions.forEachIndexed { index, action ->
                    HeaderButton(action, index, single = state.actions.size == 1, enabled = !state.busy, actions.onAction)
                }
            }
        }
    }
}

@Composable
private fun RolePill(role: ProfileRole) {
    val tone = when (role.kind) {
        ProfileRoleKind.POSITION -> Tone.Primary
        ProfileRoleKind.STUDY -> Tone.Tertiary
        ProfileRoleKind.FRIEND -> Tone.Success
        ProfileRoleKind.OWN -> Tone.Neutral
    }
    val icon = if (role.kind == ProfileRoleKind.FRIEND) painterResource(Res.drawable.ic_check) else null
    StatusPill(role.text.asString(), tone = tone, icon = icon)
}

/** `ИСУ 288104 ⧉`: a tap copies the number, which the host confirms. */
@Composable
private fun IsuLine(isu: Int, onCopy: (Int) -> Unit) {
    val label = text(SocialStrings.person_isu_copy, isu)
    Row(
        Modifier
            .padding(top = ItmoTheme.spacing.related)
            .clip(ItmoTheme.shapes.full)
            .clickable(onClickLabel = label) { onCopy(isu) }
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.compact)
            .testTag(UserProfileTestTags.ISU)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        Text(
            text(SocialStrings.user_subtitle_isu, isu),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
        Icon(
            painterResource(Res.drawable.ic_content_copy),
            contentDescription = null,
            Modifier.size(CopyIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The first button is filled, the second tonal; two share the width, so only one alone carries its icon. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderButton(
    action: CardAction,
    index: Int,
    single: Boolean,
    enabled: Boolean,
    onAction: (CardAction) -> Unit,
) {
    val label = action.label.asString()
    val modifier = Modifier.weight(1f).testTag(if (index == 0) UserProfileTestTags.PRIMARY else UserProfileTestTags.SECONDARY)
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (single) {
            Icon(painterResource(action.icon()), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(label, maxLines = 1)
    }
    if (index == 0) {
        Button(onClick = { onAction(action) }, modifier = modifier, enabled = enabled, content = content)
    } else {
        TonalButton(onClick = { onAction(action) }, modifier = modifier, enabled = enabled, content = content)
    }
}

private fun CardAction.icon(): DrawableResource = when (this) {
    CardAction.WRITE_REVIEW -> Res.drawable.ic_edit_note
    CardAction.ADD_FRIEND -> Res.drawable.ic_add
    CardAction.ACCEPT_REQUEST -> Res.drawable.ic_check
    CardAction.REJECT_REQUEST -> Res.drawable.ic_close
    CardAction.CANCEL_REQUEST -> Res.drawable.ic_undo
    CardAction.INVITE -> Res.drawable.ic_share
}

/** "Преподавание": the subjects they teach the viewer and where to find them, then the reviews with their summary. */
private fun LazyListScope.teaching(state: UserProfileUiState.Content, actions: UserProfileActions) {
    val block: TeachingBlock = state.teaching ?: return
    item(key = "teaching:title", contentType = "title") {
        SectionHeader(text(SocialStrings.person_section_teaching), Modifier.testTag(UserProfileTestTags.TEACHING))
    }
    block.now?.let { now ->
        item(key = "teaching:now", contentType = "now") {
            // The card's one hero: where to find the teacher right now.
            ItmoCard(Modifier.testTag(UserProfileTestTags.NOW), tone = Tone.Primary, hero = true) {
                val quiet = LocalContentColor.current.copy(alpha = QUIET_ALPHA)
                Text(now.heading.asString(), style = ItmoTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(now.place.asString(), style = ItmoTheme.emphasizedTypography.headlineSmall)
                Text(now.lesson.asString(), color = quiet, style = ItmoTheme.typography.bodyMedium)
            }
        }
    }
    if (block.subjects.isNotEmpty() || block.rooms.isNotEmpty()) {
        item(key = "teaching", contentType = "rows") {
            ListGroup {
                block.subjects.forEach { subject ->
                    item {
                        ListRow(
                            subject,
                            subtitle = text(SocialStrings.person_teaches_you),
                            icon = painterResource(Res.drawable.ic_school),
                            iconTone = Tone.Primary,
                        )
                    }
                }
                block.rooms.forEach { room ->
                    item {
                        ListRow(
                            text(SocialStrings.person_section_rooms),
                            subtitle = listOfNotNull(room.title.asString(), room.detail).joinToString(", "),
                            icon = painterResource(Res.drawable.ic_location_on),
                            iconTone = Tone.Neutral,
                        )
                    }
                }
            }
        }
    }
    state.reviews?.let { reviews(it, actions) }
}

/** The heading with the count, the AI summary, the own review, then the others' reviews as one group. */
private fun LazyListScope.reviews(reviews: ProfileReviews, actions: UserProfileActions) {
    if (reviews.count == 0 && reviews.summary == null) return
    item(key = "reviews:heading", contentType = "title") {
        SectionHeader(
            text(SocialStrings.teacher_reviews_title),
            Modifier.testTag(UserProfileTestTags.REVIEWS),
            count = reviews.count.takeIf { it > 0 }?.toString(),
        )
    }
    reviews.summary?.let { summary ->
        item(key = "reviews:summary", contentType = "summary") {
            TeacherSummaryCard(summary, reviews.summaryExpanded, actions.onToggleSummary)
        }
    }
    reviews.mine?.let { mine ->
        item(key = "reviews:own", contentType = "own-review") {
            OwnTeacherReviewRow(mine, busy = reviews.busyId == mine.id, actions.review)
        }
    }
    if (reviews.items.isNotEmpty()) {
        // One connected group: its rows sit 2 dp apart, not the column's gap; a vote keeps each row by its id.
        item(key = "reviews:list", contentType = "reviews") {
            Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                reviews.items.forEachIndexed { index, review ->
                    key(review.id, index) {
                        TeacherReviewRow(
                            review,
                            GroupPosition.of(index, reviews.items.size),
                            canVote = reviews.canVote,
                            canReport = reviews.canReport,
                            busy = reviews.busyId == review.id,
                            actions = actions.review,
                        )
                    }
                }
            }
        }
    }
}

/** "Учёба": the groups, then the schedule, sport and friends the viewer may open. */
private fun LazyListScope.study(block: StudyBlock?, actions: UserProfileActions) {
    if (block == null) return
    item(key = "study:title", contentType = "title") {
        SectionHeader(text(SocialStrings.person_section_education), Modifier.testTag(UserProfileTestTags.STUDY))
    }
    item(key = "study", contentType = "rows") {
        ListGroup {
            block.groups.forEach { group ->
                item {
                    ListRow(
                        group.title.asString(),
                        subtitle = group.detail,
                        icon = painterResource(Res.drawable.ic_group),
                        iconTone = Tone.Tertiary,
                    )
                }
            }
            if (block.schedule) item { OpenRow(CoreStrings.user_profile_schedule_title, Res.drawable.ic_calendar_month, UserProfileTestTags.SCHEDULE, actions.onSchedule) }
            if (block.sport) item { OpenRow(CoreStrings.user_profile_sport_title, Res.drawable.ic_sprint, UserProfileTestTags.SPORT, actions.onSport) }
            if (block.friends) item { OpenRow(SocialStrings.friends_title, Res.drawable.ic_group, UserProfileTestTags.FRIENDS, actions.onFriends) }
        }
    }
}

@Composable
private fun OpenRow(title: String, icon: DrawableResource, tag: String, onClick: () -> Unit) {
    ListRow(
        text(title),
        Modifier.testTag(tag),
        icon = painterResource(icon),
        iconTone = Tone.Primary,
        chevron = true,
        onClick = onClick,
    )
}

private const val SKELETON_ROWS = 3
/** Secondary lines on the tinted hero, as on Сегодня. */
private const val QUIET_ALPHA = 0.75f
private val AvatarSize = 96.dp
private val CopyIconSize = 16.dp
/** The gap between rows of a connected group (design.md, Connected groups). */
private val GroupGap = 2.dp
