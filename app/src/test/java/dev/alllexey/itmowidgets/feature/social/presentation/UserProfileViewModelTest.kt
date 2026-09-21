package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the profile and marks the signed-in user's own page`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSocialRepository().apply {
            profiles = mapOf(5 to profile(5, RelationshipState.NONE))
        }

        val other = UserProfileViewModel(handle(5), repository, currentUser(1))
        advanceUntilIdle()
        assertEquals(UserProfileUiState.Content(profile(5), isSelf = false, busy = false), other.uiState.value)

        val self = UserProfileViewModel(handle(5), repository, currentUser(5))
        advanceUntilIdle()
        assertEquals(true, (self.uiState.value as UserProfileUiState.Content).isSelf)
    }

    @Test
    fun `a person seen in a list opens with content and the network only updates it`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeSocialRepository().apply {
            cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS))
            profiles = mapOf(5 to profile(5, RelationshipState.NONE))
            profileGate = { gate.await() }
        }
        val viewModel = UserProfileViewModel(handle(5), repository, currentUser(1))
        runCurrent()
        assertEquals(UserProfileUiState.Content(profile(5, RelationshipState.FRIENDS), isSelf = false, busy = false), viewModel.uiState.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(UserProfileUiState.Content(profile(5), isSelf = false, busy = false), viewModel.uiState.value)
    }

    @Test
    fun `a failed refresh keeps the seeded page unless access was revoked`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSocialRepository().apply {
            cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS))
            profiles = emptyMap()
        }
        val revoked = UserProfileViewModel(handle(5), repository, currentUser(1))
        advanceUntilIdle()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), revoked.uiState.value)

        repository.profileError = AppError.Network
        val offline = UserProfileViewModel(handle(5), repository, currentUser(1))
        val failure = async { offline.eventFlow.first() }
        advanceUntilIdle()
        assertEquals(UserProfileUiState.Content(profile(5, RelationshipState.FRIENDS), isSelf = false, busy = false), offline.uiState.value)
        assertEquals(UserProfileEvent.ActionFailed(AppError.Network), failure.await())
    }

    @Test
    fun `primary action follows the relationship state`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSocialRepository().apply {
            profiles = mapOf(5 to profile(5, RelationshipState.NONE))
        }
        val viewModel = UserProfileViewModel(handle(5), repository, currentUser(1))
        advanceUntilIdle()

        viewModel.onPrimaryAction()
        advanceUntilIdle()
        assertEquals(RelationshipState.OUTGOING, viewModel.relationship())

        viewModel.onPrimaryAction()
        advanceUntilIdle()
        assertEquals(RelationshipState.NONE, viewModel.relationship())
        assertEquals(listOf("send:5", "cancel:5"), repository.actions)

        repository.profiles = mapOf(5 to profile(5, RelationshipState.INCOMING))
        viewModel.load()
        advanceUntilIdle()
        viewModel.onSecondaryAction()
        advanceUntilIdle()
        assertEquals(listOf("send:5", "cancel:5", "reject:5"), repository.actions)
    }

    @Test
    fun `removing a friend requires confirmation and failures restore the profile`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSocialRepository().apply {
                profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS))
            }
            val viewModel = UserProfileViewModel(handle(5), repository, currentUser(1))
            advanceUntilIdle()

            viewModel.onPrimaryAction()
            assertEquals(UserProfileEvent.ConfirmRemove("Пользователь 5"), viewModel.eventFlow.first())
            assertEquals(emptyList<String>(), repository.actions)

            repository.actionError = AppError.Network
            viewModel.removeFriend()
            assertEquals(UserProfileEvent.ActionFailed(AppError.Network), viewModel.eventFlow.first())
            advanceUntilIdle()
            val content = viewModel.uiState.value as UserProfileUiState.Content
            assertEquals(RelationshipState.FRIENDS, content.profile.relationship)
            assertEquals(false, content.busy)
        }

    @Test
    fun `unknown users are reported as not found`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = UserProfileViewModel(handle(404), FakeSocialRepository(), currentUser(1))
        advanceUntilIdle()

        assertEquals(UserProfileUiState.Error(AppError.NotFound), viewModel.uiState.value)
    }

    private fun UserProfileViewModel.relationship() =
        (uiState.value as UserProfileUiState.Content).profile.relationship

    private fun handle(isu: Int) = SavedStateHandle(mapOf(UserScreenArgs.ISU to isu))

    private fun currentUser(isu: Int) = object : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser = CurrentUser(isu, "Я", null)
    }
}
