package dev.alllexey.itmowidgets.feature.settings.presentation.connection

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.data.BarsStatusRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.presentation.FakeSettingsRepository
import dev.alllexey.itmowidgets.feature.settings.presentation.FakeWidgetRefreshRequester
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsPlurals
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** «Подключение и данные»: the consent-gated switch, the audiences with their minimum loading time, BARS, friends. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private class Fixture(connected: Boolean, sharing: SharingSettingsState) {
        val stores = PreferenceStores()
        val services = FakeCustomServicesRepository(enabled = connected)
        val settings = FakeSettingsRepository(LocalSettings(customServicesEnabled = connected), sharing, true, MutableStateFlow(true))
        val social = FakeSocialRepository()
        val refresher = FakeWidgetRefreshRequester()
        val viewModel = ConnectionViewModel(services, settings, social, BarsStatusRepositoryImpl(stores.markSources), refresher)
    }

    private fun fixture(connected: Boolean = true, sharing: SharingSettingsState = Audiences) = Fixture(connected, sharing)

    @Test
    fun notConnectedShowsOnlyYouAndForgetsTheAudiences() = runTest(main.dispatcher) {
        val fixture = fixture(connected = false)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.loaded)
        assertEquals(false, state.connected)
        assertEquals(ConnectionPrivacy.OnlyYou, state.privacy)
        assertEquals(null, state.friends)
        assertEquals(0, fixture.settings.refreshSharingCount)
        assertEquals(1, fixture.settings.disableSharingCount)
    }

    @Test
    fun connectingLoadsTheAudiencesForAtLeast300Ms() = runTest(main.dispatcher) {
        val fixture = fixture()
        runCurrent()
        assertEquals(1, fixture.settings.refreshSharingCount)
        assertEquals(ConnectionPrivacy.Loading, fixture.viewModel.uiState.value.privacy)
        advanceTimeBy(299)
        runCurrent()
        assertEquals(ConnectionPrivacy.Loading, fixture.viewModel.uiState.value.privacy)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            ConnectionPrivacy.Audiences(SharingVisibility.FRIENDS, SharingVisibility.NOBODY, SharingVisibility.ALL),
            fixture.viewModel.uiState.value.privacy,
        )
    }

    @Test
    fun theSwitchTurnsOnAndOffAndRedrawsTheWidgets() = runTest(main.dispatcher) {
        val fixture = fixture(connected = false)
        advanceUntilIdle()

        fixture.viewModel.setConnected(true)
        advanceUntilIdle()
        assertEquals(true, fixture.viewModel.uiState.value.connected)
        fixture.viewModel.setConnected(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), fixture.services.requests)
        assertEquals(2, fixture.refresher.refreshCount)
        assertEquals(ConnectionPrivacy.OnlyYou, fixture.viewModel.uiState.value.privacy)
    }

    @Test
    fun aFailedSwitchSaysSoAndChangesNothing() = runTest(main.dispatcher) {
        val fixture = fixture(connected = false)
        fixture.services.failing = true
        advanceUntilIdle()

        fixture.viewModel.setConnected(true)
        advanceUntilIdle()

        assertTrue(fixture.viewModel.events.first() is ConnectionEvent.ShowError)
        assertEquals(false, fixture.viewModel.uiState.value.connected)
        assertEquals(0, fixture.refresher.refreshCount)
    }

    @Test
    fun anAudienceIsSavedOnceAndAFailureSaysSo() = runTest(main.dispatcher) {
        val fixture = fixture()
        advanceUntilIdle()

        fixture.viewModel.setAudience(PrivacyTopic.SCHEDULE, SharingVisibility.FRIENDS)
        fixture.viewModel.setAudience(PrivacyTopic.SPORT, SharingVisibility.ALL)
        advanceUntilIdle()
        assertEquals(emptyList(), fixture.settings.scheduleSharingRequests, "the same audience is no change")
        assertEquals(listOf(SharingVisibility.ALL), fixture.settings.sportSharingRequests)

        fixture.settings.sportSharingResult = AppResult.Failure(AppError.Network)
        fixture.viewModel.setAudience(PrivacyTopic.FRIENDS, SharingVisibility.NOBODY)
        advanceUntilIdle()
        assertEquals(listOf(SharingVisibility.NOBODY), fixture.settings.friendsSharingRequests)
        assertEquals(ConnectionEvent.ShowError(AppError.Network), fixture.viewModel.events.first())
    }

    @Test
    fun aFailedLoadCanBeRetried() = runTest(main.dispatcher) {
        val fixture = fixture(sharing = SharingSettingsState.Error)
        advanceUntilIdle()
        assertEquals(ConnectionPrivacy.Failed, fixture.viewModel.uiState.value.privacy)

        fixture.viewModel.retryPrivacy()
        runCurrent()
        assertEquals(ConnectionPrivacy.Loading, fixture.viewModel.uiState.value.privacy)
        fixture.settings.sharing.value = Audiences
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.privacy is ConnectionPrivacy.Audiences)
        assertEquals(2, fixture.settings.refreshSharingCount)
    }

    @Test
    fun barsFollowsItsFirstAnswerAndTheEndedSession() = runTest(main.dispatcher) {
        val fixture = fixture()
        advanceUntilIdle()
        assertEquals(BarsConnection.NOT_CONNECTED, fixture.viewModel.uiState.value.bars)

        fixture.stores.markSources.setBarsMarksEnabled(true)
        advanceUntilIdle()
        assertEquals(BarsConnection.CONNECTED, fixture.viewModel.uiState.value.bars)

        fixture.stores.markSources.setBarsLoginPrompt(BarsLoginPrompt.PENDING)
        advanceUntilIdle()
        assertEquals(BarsConnection.EXPIRED, fixture.viewModel.uiState.value.bars)
    }

    @Test
    fun theFriendsRowCountsFriendsAndNewRequestsWhileConnected() = runTest(main.dispatcher) {
        val fixture = fixture()
        fixture.social.friends.value = LoadState.Content(listOf(profile(1), profile(2), profile(3)))
        fixture.social.requests.value = LoadState.Content(FriendRequests(incoming = listOf(profile(4)), outgoing = emptyList()))
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Plural(SettingsPlurals.connection_friends_count, 3, listOf(3)),
                    UiText.Plural(SettingsPlurals.connection_friend_requests_count, 1, listOf(1)),
                ),
                ", ",
            ),
            state.friends,
        )
        assertEquals(1, state.friendRequests)
        assertTrue(fixture.social.refreshes > 0)
    }

    @Test
    fun deletingTheAccountOpensTheSitePageEvenWhileNotConnected() = runTest(main.dispatcher) {
        val fixture = fixture(connected = false)
        advanceUntilIdle()

        fixture.viewModel.deleteAccount()

        assertEquals(ConnectionEvent.OpenWebPage("/delete-account"), fixture.viewModel.events.first())
    }

    private companion object {
        val Audiences = SharingSettingsState.Content(
            SharingSettings(SharingVisibility.FRIENDS, SharingVisibility.NOBODY, SharingVisibility.ALL),
        )
    }
}
