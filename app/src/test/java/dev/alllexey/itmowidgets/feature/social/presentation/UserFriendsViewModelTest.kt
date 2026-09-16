package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UserFriendsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `target list retains viewer relationships without exposing mutation controls`() = runTest {
        val repository = FakeSocialRepository().apply {
            userFriendsResult = AppResult.Success(listOf(profile(12, RelationshipState.INCOMING)))
        }
        val vm = create(repository)
        assertEquals(UserFriendsUiState.Loading, vm.uiState.value)
        vm.load()
        advanceUntilIdle()
        assertEquals(listOf(42), repository.userFriendsCalls)
        val row = ((vm.uiState.value as UserFriendsUiState.Content).items.single() as UserListItem.User).row
        assertEquals(12, row.isu)
        assertNotNull(row.status)
        assertNull(row.primary)
        assertNull(row.secondary)
        assertTrue(row.opensProfile)
    }

    @Test fun `empty first failure and retry remain distinct`() = runTest {
        val repository = FakeSocialRepository().apply { userFriendsResult = AppResult.Failure(AppError.Network) }
        val vm = create(repository)
        advanceUntilIdle()
        assertEquals(UserFriendsUiState.Error(AppError.Network), vm.uiState.value)
        repository.userFriendsResult = AppResult.Success(emptyList())
        vm.load()
        advanceUntilIdle()
        assertEquals(UserFriendsUiState.Content(emptyList()), vm.uiState.value)
    }

    @Test fun `network refresh preserves content but revoked access removes it`() = runTest {
        for (revoked in listOf(AppError.Forbidden, AppError.CustomServicesDisabled, AppError.Unauthorized, AppError.NotFound)) {
            val repository = FakeSocialRepository().apply { userFriendsResult = AppResult.Success(listOf(profile(12, RelationshipState.NONE))) }
            val vm = create(repository)
            advanceUntilIdle()
            val content = vm.uiState.value
            repository.userFriendsResult = AppResult.Failure(AppError.Network)
            val error = async { vm.refreshErrors.first() }
            vm.load()
            assertTrue((vm.uiState.value as UserFriendsUiState.Content).refreshing)
            advanceUntilIdle()
            assertEquals(content, vm.uiState.value)
            assertEquals(AppError.Network, error.await())
            repository.userFriendsResult = AppResult.Failure(revoked)
            vm.load()
            advanceUntilIdle()
            assertEquals(UserFriendsUiState.Error(revoked), vm.uiState.value)
        }
    }

    private fun create(repository: FakeSocialRepository) = UserFriendsViewModel(
        SavedStateHandle(mapOf(UserScreenArgs.ISU to 42, UserScreenArgs.NAME to "Длинное имя")), repository
    )
}
