package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.core.testing.ownReview
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.FakePersonRepository
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.ui.reviews.TeacherReviewTestTags
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.alllexey.itmowidgets.feature.social.ui.profile.preview.UserProfilePreviewData as P

/** `UserProfileRoute` with the real ViewModel over fakes: its snackbars, its dialogs and the page its exits get. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserProfileRouteTest {

    @Test
    fun aFailedPartOffersOneRetryThatLoadsIt() = runComposeUiTest {
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(P.ISU to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(reviews = reviews)
        setRoute(viewModel)

        waitUntil { content(viewModel) != null }
        onNodeWithText("Часть данных не загрузилась").assertExists()
        assertEquals(null, content(viewModel)?.reviews)

        reviews.results = mapOf(P.ISU to AppResult.Success(P.teacherReviews))
        onNodeWithText("Повторить").performClick()
        waitUntil { content(viewModel)?.reviews != null }
        onNodeWithText("Часть данных не загрузилась").assertDoesNotExist()
        assertEquals(2, reviews.calls)
    }

    @Test
    fun removingAFriendAsksFirstAndACancelLeavesTheFriendship() = runComposeUiTest {
        val social = FakeSocialRepository().apply { profiles = mapOf(P.ISU to P.studentProfile(RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social = social)
        setRoute(viewModel)
        waitUntil { content(viewModel)?.social != null }

        scrollTo(UserProfileTestTags.REMOVE)
        onNodeWithTag(UserProfileTestTags.REMOVE).performClick()
        onNodeWithText("Удалить из друзей?").assertExists()
        onNodeWithText("${P.LONG_NAME} перестанет видеть ваши данные для друзей, а вы — его.").assertExists()
        onNodeWithText("Отмена").performClick()
        onNode(isDialog()).assertDoesNotExist()
        assertEquals(emptyList<String>(), social.actions)

        onNodeWithTag(UserProfileTestTags.REMOVE).performClick()
        onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        waitUntil { social.actions.isNotEmpty() }
        onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf("remove:${P.ISU}"), social.actions)
    }

    @Test
    fun deletingTheOwnReviewAsksFirst() = runComposeUiTest {
        val withOwn = P.teacherReviews.copy(reviews = emptyList(), mine = ownReview())
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(P.ISU to AppResult.Success(withOwn))
            deleteResult = AppResult.Success(withOwn.copy(mine = null))
        }
        val viewModel = viewModel(reviews = reviews)
        setRoute(viewModel)
        waitUntil { content(viewModel)?.reviews?.mine != null }

        scrollTo(TeacherReviewTestTags.OWN_REVIEW)
        onNodeWithContentDescription("Действия с отзывом").performClick()
        onNodeWithText("Удалить").performClick()
        onNodeWithText("Удалить отзыв?").assertExists()
        onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        waitUntil { content(viewModel)?.reviews?.mine == null }
        onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf("delete:${P.ISU}"), reviews.actions)
    }

    @Test
    fun aFailedActionSaysWhyInASnackbar() = runComposeUiTest {
        val social = FakeSocialRepository().apply {
            profiles = mapOf(P.ISU to P.studentProfile(RelationshipState.NONE))
            actionError = AppError.Restricted
        }
        val viewModel = viewModel(social = social)
        setRoute(viewModel)
        waitUntil { content(viewModel)?.social != null }

        onNodeWithTag(UserProfileTestTags.PRIMARY).performClick()
        waitUntil { social.actions.isNotEmpty() }
        onNodeWithText("Не получилось: Действие ограничено модерацией").assertExists()
    }

    @Test
    fun theExitsGetThePageOnScreen() = runComposeUiTest {
        val writable = P.teacherReviews.copy(canWrite = true, knownTeacher = true)
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(P.ISU to AppResult.Success(writable)) }
        val viewModel = viewModel(reviews = reviews)
        val opened = mutableListOf<String>()
        setRoute(
            viewModel,
            UserProfileExits(
                onShare = { opened += "share:${it.isu}" },
                onCopyIsu = { opened += "copy:$it" },
                onReviewEditor = { opened += "editor:${it.isu}:${it.name}" },
                onSource = { opened += "source:$it" },
            ),
        )
        waitUntil { content(viewModel)?.reviews != null }

        onNodeWithTag(UserProfileTestTags.SHARE).performClick()
        onNodeWithTag(UserProfileTestTags.ISU).performClick()
        scrollTo(UserProfileTestTags.WRITE_REVIEW)
        onNodeWithTag(UserProfileTestTags.WRITE_REVIEW).performClick()

        assertEquals(listOf("share:${P.ISU}", "copy:${P.ISU}", "editor:${P.ISU}:${P.LONG_NAME}"), opened)
    }

    private fun ComposeUiTest.setRoute(viewModel: UserProfileViewModel, exits: UserProfileExits = UserProfileExits()) {
        setContent { ItmoTheme(platformStyle = ItmoPlatformStyle.Material) { UserProfileRoute(exits, viewModel = viewModel) } }
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(UserProfileTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    private fun content(viewModel: UserProfileViewModel) = viewModel.uiState.value as? UserProfileUiState.Content

    private fun viewModel(
        social: FakeSocialRepository = FakeSocialRepository(),
        reviews: FakeTeacherReviewsRepository = FakeTeacherReviewsRepository(),
    ): UserProfileViewModel {
        val people = FakePersonRepository().apply {
            val person = if (social.profiles.isEmpty()) P.teacher else P.student
            this.people = mapOf(P.ISU to AppResult.Success(person))
        }
        return UserProfileViewModel(
            SavedStateHandle(mapOf(UserScreenArgs.ISU to P.ISU)),
            social,
            people,
            reviews,
            object : CurrentUserProvider {
                override suspend fun getCurrentUser() = CurrentUser(VIEWER, "Я", null)
            },
        )
    }

    private companion object {
        const val VIEWER = 1
    }
}

