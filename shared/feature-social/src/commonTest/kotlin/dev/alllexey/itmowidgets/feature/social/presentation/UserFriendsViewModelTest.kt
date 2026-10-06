package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.user_name_placeholder
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserFriendsViewModelTest {
    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test fun targetListRetainsViewerRelationshipsWithoutExposingMutationControls() = runTest {
        val repository = FakeSocialRepository().apply {
            userFriendsResult = AppResult.Success(listOf(profile(12, RelationshipState.INCOMING)))
        }
        val vm = create(repository)
        assertEquals(UserFriendsUiState.Loading, vm.uiState.value)
        vm.refresh(RefreshMode.Pull)
        advanceUntilIdle()
        assertEquals(listOf(42), repository.userFriendsCalls)
        val row = ((vm.uiState.value as UserFriendsUiState.Content).items.single() as UserListItem.User).row
        assertEquals(12, row.isu)
        assertNotNull(row.status)
        assertNull(row.primary)
        assertNull(row.secondary)
        assertTrue(row.opensProfile)
    }

    @Test fun aListSeenBeforeOpensWithContentWhileTheNetworkRefreshesIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeSocialRepository().apply {
            cachedUserFriends = mapOf(42 to listOf(profile(12, RelationshipState.NONE)))
            userFriendsResult = AppResult.Success(listOf(profile(12, RelationshipState.NONE), profile(13, RelationshipState.NONE)))
            userFriendsGate = { gate.await() }
        }
        val vm = create(repository)
        runCurrent()
        val seeded = vm.uiState.value as UserFriendsUiState.Content
        assertFalse(seeded.refreshing)
        assertEquals(listOf(12), seeded.items.map { (it as UserListItem.User).row.isu })
        gate.complete(Unit)
        advanceUntilIdle()
        val fresh = vm.uiState.value as UserFriendsUiState.Content
        assertFalse(fresh.refreshing)
        assertEquals(listOf(12, 13), fresh.items.map { (it as UserListItem.User).row.isu })
        assertEquals(listOf(42), repository.userFriendsCalls)
    }

    @Test fun emptyFirstFailureAndRetryRemainDistinct() = runTest {
        val repository = FakeSocialRepository().apply { userFriendsResult = AppResult.Failure(AppError.Network) }
        val vm = create(repository)
        advanceUntilIdle()
        assertEquals(UserFriendsUiState.Error(AppError.Network), vm.uiState.value)
        repository.userFriendsResult = AppResult.Success(emptyList())
        vm.refresh(RefreshMode.Force)
        advanceUntilIdle()
        assertEquals(UserFriendsUiState.Content(emptyList()), vm.uiState.value)
    }

    @Test fun networkRefreshPreservesContentButRevokedAccessRemovesIt() = runTest {
        val revokedStates = mapOf(
            AppError.Forbidden to UserFriendsUiState.Hidden,
            AppError.CustomServicesDisabled to UserFriendsUiState.Disabled,
            AppError.Unauthorized to UserFriendsUiState.Error(AppError.Unauthorized),
            AppError.NotFound to UserFriendsUiState.Error(AppError.NotFound)
        )
        for ((revoked, revokedState) in revokedStates) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeSocialRepository().apply { userFriendsResult = AppResult.Success(listOf(profile(12, RelationshipState.NONE))) }
            val vm = create(repository)
            val events = events(vm)
            advanceUntilIdle()
            val content = vm.uiState.value
            repository.userFriendsResult = AppResult.Failure(AppError.Network)
            repository.userFriendsGate = { gate.await() }
            vm.refresh(RefreshMode.Pull)
            runCurrent()
            assertTrue((vm.uiState.value as UserFriendsUiState.Content).refreshing)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(content, vm.uiState.value)
            assertEquals(listOf<UserFriendsEvent>(UserFriendsEvent.RefreshFailed(AppError.Network)), events)
            repository.userFriendsResult = AppResult.Failure(revoked)
            vm.refresh(RefreshMode.Pull)
            advanceUntilIdle()
            assertEquals(revokedState, vm.uiState.value)
            assertEquals(1, events.size)
        }
    }

    @Test fun deniedAndDisabledListsAreTheirOwnStatesAndARetryOverThemShowsProgress() = runTest {
        for ((error, state) in listOf(
            AppError.Forbidden to UserFriendsUiState.Hidden,
            AppError.CustomServicesDisabled to UserFriendsUiState.Disabled
        )) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeSocialRepository().apply { userFriendsResult = AppResult.Failure(error) }
            val vm = create(repository)
            advanceUntilIdle()
            assertEquals(state, vm.uiState.value)

            repository.userFriendsGate = { gate.await() }
            vm.refresh(RefreshMode.Force)
            runCurrent()
            assertEquals(UserFriendsUiState.Loading, vm.uiState.value)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(state, vm.uiState.value)
        }
    }

    @Test fun anEmptyNameInTheListFallsBackToThePlaceholder() = runTest {
        val unnamed = profile(12, RelationshipState.NONE).let { it.copy(user = it.user.copy(name = "")) }
        val repository = FakeSocialRepository().apply { userFriendsResult = AppResult.Success(listOf(unnamed)) }
        val vm = create(repository)
        advanceUntilIdle()
        val row = ((vm.uiState.value as UserFriendsUiState.Content).items.single() as UserListItem.User).row
        assertEquals(UiText.Res(Res.string.user_name_placeholder, listOf(12)), row.displayName)
        assertEquals(UiText.Dynamic("Пользователь 12"), profile(12).user.displayName())
    }

    private fun TestScope.events(vm: UserFriendsViewModel) = mutableListOf<UserFriendsEvent>().also { events ->
        backgroundScope.launch { vm.events.toList(events) }
    }

    private fun create(repository: FakeSocialRepository) = UserFriendsViewModel(
        SavedStateHandle(mapOf(UserScreenArgs.ISU to 42, UserScreenArgs.NAME to "Длинное имя")), repository
    )
}
