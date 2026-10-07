package dev.alllexey.itmowidgets.app.shell.entries

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The ViewModels of the social keys over synthetic data, for every shell test whose screens open them: the viewer
 * has one friend, [SocialSamples.FRIEND], whose profile, friends and schedule are open; search finds nobody, the
 * directory knows nobody and nobody has reviews. [history] records the friend picker's choices.
 */
internal fun socialTestModule(history: FriendSelectionHistory = RecordingHistory()): Module = module {
    viewModel { FriendsViewModel(OneFriendSocial) }
    viewModel { UserSearchViewModel(NoPeople, OneFriendSocial) }
    viewModel { UserFriendsViewModel(get<SavedStateHandle>(), OneFriendSocial) }
    viewModel { UserProfileViewModel(get<SavedStateHandle>(), OneFriendSocial, NoPerson, NoReviews, Viewer) }
    viewModel { FriendSelectorViewModel(OneFriend, history, NoPeople, get<SavedStateHandle>()) }
}

internal object SocialSamples {
    const val VIEWER_ISU = 300001
    const val FRIEND_ISU = 300002
    const val FRIEND_NAME = "Иван Петров"
    val FRIEND = UserProfile(
        UserSummary(
            isu = FRIEND_ISU,
            name = FRIEND_NAME,
            pictureUrl = null,
            groups = listOf(UserGroup("M3100", 1, "ФИТиП")),
            sharing = UserSharing(sport = true, schedule = true, friends = true),
        ),
        RelationshipState.FRIENDS,
    )
}

private object OneFriendSocial : SocialRepository {
    override fun observeFriends(): Flow<LoadState<List<UserProfile>>> =
        flowOf(LoadState.Content(listOf(SocialSamples.FRIEND)))
    override fun observeRequests(): Flow<LoadState<FriendRequests>> =
        flowOf(LoadState.Content(FriendRequests.EMPTY))
    override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
    override val currentFriends: List<UserProfile> = listOf(SocialSamples.FRIEND)
    override suspend fun refresh() = Unit
    override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> =
        AppResult.Success(listOf(SocialSamples.FRIEND))
    override suspend fun profile(isu: Int): AppResult<UserProfile> =
        if (isu == SocialSamples.FRIEND_ISU) AppResult.Success(SocialSamples.FRIEND)
        else AppResult.Failure(AppError.NotFound)
    override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> =
        AppResult.Success(listOf(SocialSamples.FRIEND).filter { it.isu in isus })
    override suspend fun sendRequest(isu: Int): AppResult<UserProfile> = error("no actions here")
    override suspend fun acceptRequest(isu: Int): AppResult<UserProfile> = error("no actions here")
    override suspend fun rejectRequest(isu: Int): AppResult<UserProfile> = error("no actions here")
    override suspend fun cancelRequest(isu: Int): AppResult<UserProfile> = error("no actions here")
    override suspend fun removeFriend(isu: Int): AppResult<UserProfile> = error("no actions here")
}

private object OneFriend : FriendRepository {
    override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> =
        flowOf(LoadState.Content(listOf(SocialSamples.FRIEND.user)))
    override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
    override suspend fun refreshFriendList() = Unit
    override val currentFriends: List<UserSummary> = listOf(SocialSamples.FRIEND.user)
}

internal class RecordingHistory : FriendSelectionHistory {
    val recorded = mutableListOf<Int>()
    override suspend fun getRecentIsu(): List<Int> = emptyList()
    override suspend fun record(isu: Int) {
        recorded += isu
    }
}

private object NoPeople : PeopleSearchRepository {
    override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> =
        AppResult.Success(PeopleSearchPage.EMPTY)
}

private object NoPerson : PersonRepository {
    override fun cachedPerson(isu: Int): Person? = null
    override suspend fun person(isu: Int): AppResult<Person> = AppResult.Failure(AppError.NotFound)
}

private object NoReviews : TeacherReviewsRepository {
    override fun cachedReviews(isu: Int): TeacherReviews? = null
    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> = AppResult.Failure(AppError.NotFound)
    override fun observeUpdates(): Flow<TeacherReviews> = emptyFlow()
    override suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews> = error("no reviews")
    override suspend fun delete(isu: Int): AppResult<TeacherReviews> = error("no reviews")
    override suspend fun vote(isu: Int, reviewId: String, value: Int): AppResult<TeacherReviews> =
        error("no reviews")
    override suspend fun report(
        isu: Int,
        reviewId: String,
        reason: ReviewReportReason,
        comment: String?,
    ): AppResult<TeacherReviews> = error("no reviews")
}

private object Viewer : CurrentUserProvider {
    override suspend fun getCurrentUser() =
        CurrentUser(isu = SocialSamples.VIEWER_ISU, name = "Студент Тестовый", pictureUrl = null)
}
