package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFact
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileReviews
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.reviews.OwnTeacherReviewRow
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherReviewActions
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherReviewRow
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherSummaryCard
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.share_action
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_not_found_title
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * What the person profile reports, in `UserProfileViewModel`'s terms. [onPrimaryAction] is the hero's filled or tonal
 * button (add, cancel, accept), [onSecondaryAction] its `Отклонить`; [onRemoveFriend] is `Удалить из друзей`, which
 * the ViewModel confirms before it acts. [onFriends], [onSchedule] and [onSport] fire only for open rows. Every effect
 * (share, clipboard, navigation) belongs to the host.
 */
class UserProfileActions(
    val onBack: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCopyIsu: (isu: Int) -> Unit = {},
    val onPrimaryAction: () -> Unit = {},
    val onSecondaryAction: () -> Unit = {},
    val onRemoveFriend: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onSchedule: () -> Unit = {},
    val onSport: () -> Unit = {},
    val onWriteReview: () -> Unit = {},
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
    const val BADGE = "user_profile_badge"
    const val PRIMARY = "user_profile_primary"
    const val SECONDARY = "user_profile_secondary"
    const val REMOVE = "user_profile_remove"
    const val FRIENDS = "user_profile_friends"
    const val SCHEDULE = "user_profile_schedule"
    const val SPORT = "user_profile_sport"
    const val HIDDEN_HINT = "user_profile_hidden_hint"
    const val REVIEWS = "user_profile_reviews"
    const val WRITE_REVIEW = "user_profile_write_review"

    fun facts(kind: ProfileFactKind): String = "user_profile_facts_${kind.name.lowercase()}"
}

/**
 * One person (port of `UserProfileFragment` and `UserProfileAdapter`): the top bar with `Поделиться` for a shown page,
 * then a skeleton until the parts settle, `Профиль не найден` without an action, `Не удалось загрузить` with the reason
 * and `Повторить`, or the page. The page is a lazy list with stable keys in the order of `docs/features/social.md`
 * § Person profile: the hero card, `Должности`, `Где найти`, `ITMO.Widgets`, `Учёба`, then the reviews, so late
 * reviews append under everything without moving it. The reviews section shows only while [reviewsEnabled] (always on
 * Android; the iOS host feeds it from its platform capabilities). Stateless; the route feeds it.
 *
 * The hero is the flow's hero moment: a person who arrives while the screen is open reveals the avatar
 * ([rememberProfileHeroReveal]); a page already there on the first frame shows at once.
 */
@Composable
fun UserProfileScreen(
    state: UserProfileUiState,
    actions: UserProfileActions,
    modifier: Modifier = Modifier,
    reviewsEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val heroReveal = rememberProfileHeroReveal((state as? UserProfileUiState.Content)?.isu)
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.user_profile_title),
                navigation = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_arrow_back),
                        stringResource(CoreRes.string.common_back),
                        onClick = actions.onBack,
                    )
                },
                actions = {
                    // Only a page is worth sharing; a missing or failed profile has nothing to show the recipient.
                    if (state is UserProfileUiState.Content) {
                        AppTopBarAction(
                            painterResource(KitRes.drawable.ic_share),
                            stringResource(CoreRes.string.share_action),
                            onClick = actions.onShare,
                            modifier = Modifier.testTag(UserProfileTestTags.SHARE),
                        )
                    }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    UserProfileUiState.Loading -> Skeleton(
                        SkeletonStyle.List,
                        Modifier.testTag(UserProfileTestTags.LOADING),
                        rows = SKELETON_ROWS,
                    )
                    is UserProfileUiState.Error -> ProfileError(state.error, actions.onRetry)
                    is UserProfileUiState.Content -> ProfilePage(state, actions, reviewsEnabled, listState, heroReveal)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** A missing person has nothing to retry; any other failure says why and offers `Повторить`. */
@Composable
private fun ProfileError(error: AppError, onRetry: () -> Unit) {
    val notFound = error == AppError.NotFound
    ContentState(
        title = stringResource(
            if (notFound) Res.string.user_profile_not_found_title else CoreRes.string.common_load_error_title,
        ),
        modifier = Modifier.fillMaxSize().testTag(UserProfileTestTags.STATE),
        icon = painterResource(if (notFound) KitRes.drawable.ic_person else KitRes.drawable.ic_error),
        description = if (notFound) null else stringResource(error.textResource()),
        action = if (notFound) null else ContentStateAction(stringResource(CoreRes.string.common_retry), onRetry),
    )
}

@Composable
private fun ProfilePage(
    state: UserProfileUiState.Content,
    actions: UserProfileActions,
    reviewsEnabled: Boolean,
    listState: LazyListState,
    heroReveal: ProfileHeroReveal,
) {
    val facts = state.facts.groupBy(ProfileFact::kind)
    LazyColumn(
        Modifier.fillMaxSize().testTag(UserProfileTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.group,
        ),
    ) {
        item(key = "hero", contentType = "hero") { ProfileHero(state, actions, heroReveal) }
        factsItem(ProfileFactKind.POSITION, facts)
        factsItem(ProfileFactKind.ROOM, facts)
        state.social?.let { social ->
            item(key = "sharing", contentType = "sharing") { ProfileSharing(social, actions) }
        }
        factsItem(ProfileFactKind.EDUCATION, facts)
        state.reviews?.takeIf { reviewsEnabled }?.let { reviewsSection(it, actions) }
    }
}

private fun LazyListScope.factsItem(kind: ProfileFactKind, facts: Map<ProfileFactKind, List<ProfileFact>>) {
    val shown = facts[kind] ?: return
    item(key = "facts:$kind", contentType = "facts") { ProfileFacts(kind, shown) }
}

/**
 * The heading with the count and `Написать`, the AI summary, the own review as a group of its own 8 dp under the
 * summary, then the others' reviews as one group, 16 dp under the own review or 8 dp under the summary.
 */
private fun LazyListScope.reviewsSection(reviews: ProfileReviews, actions: UserProfileActions) {
    item(key = "reviews:heading", contentType = "reviews-heading") {
        ReviewsHeading(reviews.count, reviews.canWrite, actions.onWriteReview)
    }
    reviews.summary?.let { summary ->
        item(key = "reviews:summary", contentType = "summary") {
            TeacherSummaryCard(summary, reviews.summaryExpanded, actions.onToggleSummary)
        }
    }
    reviews.mine?.let { mine ->
        item(key = "reviews:own", contentType = "own-review") {
            val top = if (reviews.summary != null) ItmoTheme.spacing.compact else 0.dp
            OwnTeacherReviewRow(mine, busy = reviews.busyId == mine.id, actions.review, Modifier.padding(top = top))
        }
    }
    val keys = reviews.items.reviewKeys()
    reviews.items.forEachIndexed { index, review ->
        item(key = keys[index], contentType = "review") {
            val top = when {
                index > 0 -> 0.dp
                reviews.mine != null -> ItmoTheme.spacing.group
                reviews.summary != null -> ItmoTheme.spacing.compact
                else -> 0.dp
            }
            TeacherReviewRow(
                review,
                GroupPosition.of(index, reviews.items.size),
                canVote = reviews.canVote,
                canReport = reviews.canReport,
                busy = reviews.busyId == review.id,
                actions = actions.review,
                modifier = Modifier.padding(top = top),
            )
        }
    }
}

/** A review keeps its key while votes and late updates change the list; a repeated id never crashes the list. */
private fun List<TeacherReview>.reviewKeys(): List<String> {
    val seen = HashSet<String>()
    return mapIndexed { index, review ->
        val key = "review:${review.id}"
        if (seen.add(key)) key else "$key#$index"
    }
}

/** `fragment_user_profile.xml`'s `Skeleton.List` with `skeletonRows="3"`. */
private const val SKELETON_ROWS = 3
