package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_delete_account_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_delete_account_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_friends_sharing_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_all
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_friends
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_load_error
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_nobody
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_services_required
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_unknown
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sharing_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_sport_sharing_title
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Services and privacy pages built and handled by [ServicesPageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ServicesPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun friendsPrivacyStartsAllAndEditsOnlyItsOwnAudience() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings()))
        advanceUntilIdle()
        assertEquals("ALL", fixture.viewModel.choice(SettingRowId.FRIENDS_SHARING).selectedOptionKey)
        fixture.viewModel.onChoiceChanged(SettingRowId.FRIENDS_SHARING, "NOBODY")
        advanceUntilIdle()
        assertEquals(listOf(SharingVisibility.NOBODY), fixture.repository.friendsSharingRequests)
        assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
        assertTrue(fixture.repository.sportSharingRequests.isEmpty())
        assertEquals("NOBODY", fixture.viewModel.choice(SettingRowId.FRIENDS_SHARING).selectedOptionKey)
    }

    @Test
    fun doesNotExposeDefaultPrivacyChoicesWhileBackendSettingsAreLoading() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Loading
            )
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.FRIENDS)
            )
            advanceUntilIdle()

            val schedule = fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING)
            val sport = fixture.viewModel.choice(SettingRowId.SPORT_SHARING)
            assertEquals(SharingVisibility.FRIENDS.name, schedule.selectedOptionKey)
            assertEquals(SharingVisibility.FRIENDS.name, sport.selectedOptionKey)
            assertTrue(schedule.enabled)
            assertTrue(sport.enabled)
        }

    @Test
    fun customServicesToggleRefreshesWidgetsAfterApplyingTheDataGate() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SERVICES)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.CUSTOM_SERVICES, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.customServicesRepository.requests)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun sharingControlsAreDisabledWhenCustomServicesAreDisabled() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = false),
                sharing = SharingSettingsState.Disabled
            )

            advanceUntilIdle()

            val schedule = fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING)
            val sport = fixture.viewModel.choice(SettingRowId.SPORT_SHARING)
            assertFalse(schedule.enabled)
            assertEquals(null, schedule.selectedOptionKey)
            assertFalse(sport.enabled)
            assertEquals(null, sport.selectedOptionKey)
            assertEquals(UiText.Res(Res.string.settings_privacy_unknown), schedule.value)
            assertEquals(UiText.Res(Res.string.settings_privacy_unknown), sport.value)
            assertEquals(
                UiText.Res(Res.string.settings_privacy_services_required),
                fixture.viewModel.uiState.value.sections.single().footer
            )
            assertEquals(1, fixture.repository.disableSharingCount)
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun sharingContentRendersIndependentlyAndLocksWhileUpdating() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(
                    SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.NOBODY)
                )
            )
            advanceUntilIdle()

            assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
            assertTrue(
                fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).enabled
            )
            assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)
            assertTrue(fixture.viewModel.choice(SettingRowId.SPORT_SHARING).enabled)
            assertEquals(1, fixture.repository.refreshSharingCount)

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.NOBODY),
                updating = true
            )
            advanceUntilIdle()

            assertFalse(
                fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).enabled
            )
            assertFalse(fixture.viewModel.choice(SettingRowId.SPORT_SHARING).enabled)
        }

    @Test
    fun sharingUpdateFailureEmitsTheRepositoryError() =
        runTest(main.dispatcher) {
            for (key in listOf(SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING)) {
                val fixture = createFixture(
                    page = SettingsPage.PRIVACY,
                    local = LocalSettings(customServicesEnabled = true),
                    sharing = SharingSettingsState.Content(SharingSettings())
                )
                fixture.repository.scheduleSharingResult = AppResult.Failure(AppError.Network)
                fixture.repository.sportSharingResult = AppResult.Failure(AppError.Network)
                advanceUntilIdle()

                fixture.viewModel.onChoiceChanged(key, SharingVisibility.NOBODY.name)
                advanceUntilIdle()

                assertEquals(if (key == SettingRowId.SCHEDULE_SHARING) listOf(SharingVisibility.NOBODY) else emptyList<SharingVisibility>(), fixture.repository.scheduleSharingRequests)
                assertEquals(if (key == SettingRowId.SPORT_SHARING) listOf(SharingVisibility.NOBODY) else emptyList<SharingVisibility>(), fixture.repository.sportSharingRequests)
                assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
                assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)
                assertTrue(fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).enabled)
                assertTrue(fixture.viewModel.choice(SettingRowId.SPORT_SHARING).enabled)
                assertEquals(0, fixture.widgetRefresher.refreshCount)
                assertEquals(
                    SettingsEvent.ShowError(AppError.Network),
                    fixture.viewModel.events.first()
                )
            }
        }

    @Test
    fun privacyErrorExposesRetryAction() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Error
            )
            advanceUntilIdle()

            assertFalse(
                fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).enabled
            )
            assertEquals(
                UiText.Res(Res.string.settings_privacy_load_error),
                fixture.viewModel.uiState.value.sections.single().footer
            )
            fixture.viewModel.action(SettingRowId.RETRY_PRIVACY)

            fixture.viewModel.onAction(SettingRowId.RETRY_PRIVACY)
            advanceUntilIdle()

            assertEquals(2, fixture.repository.refreshSharingCount)
        }

    @Test
    fun accountDeletionFollowsTheSwitchWhetherTheConnectionIsOnOrOff() =
        runTest(main.dispatcher) {
            for (enabled in listOf(false, true)) {
                val fixture = createFixture(page = SettingsPage.SERVICES, local = LocalSettings(customServicesEnabled = enabled))
                advanceUntilIdle()

                assertEquals(
                    listOf(SettingRowId.CUSTOM_SERVICES, SettingRowId.DELETE_ACCOUNT),
                    fixture.viewModel.allItems().map { it.id }
                )
                val delete = fixture.viewModel.action(SettingRowId.DELETE_ACCOUNT)
                assertEquals(UiText.Res(Res.string.settings_delete_account_title), delete.title)
                assertEquals(UiText.Res(Res.string.settings_delete_account_description), delete.description)
                assertTrue(delete.enabled)
                // The switch keeps its own footer; the deletion row is a group of its own below it.
                assertEquals(
                    UiText.Res(Res.string.settings_services_footer),
                    fixture.viewModel.uiState.value.sections.first().footer
                )

                fixture.viewModel.onAction(SettingRowId.DELETE_ACCOUNT)
                assertEquals(SettingsEvent.OpenWebPage("/delete-account"), fixture.viewModel.events.first())
            }
        }

    @Test
    fun privacyOffersAllFriendsNobodyInOrderWithServerDefaultsAndCurrentValues() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            advanceUntilIdle()
            val choices = fixture.viewModel.allItems().filterIsInstance<SettingItem.Choice>()
            assertEquals(listOf(SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING, SettingRowId.FRIENDS_SHARING), choices.map { it.id })
            assertEquals(listOf(UiText.Res(Res.string.settings_schedule_sharing_title), UiText.Res(Res.string.settings_sport_sharing_title), UiText.Res(Res.string.settings_friends_sharing_title)), choices.map { it.title })
            choices.forEach { choice ->
                assertEquals(listOf("ALL", "FRIENDS", "NOBODY"), choice.options.map { it.key })
                assertEquals(listOf(Res.string.settings_privacy_all, Res.string.settings_privacy_friends, Res.string.settings_privacy_nobody).map(UiText::Res), choice.options.map { it.label })
                val isFriends = choice.id == SettingRowId.FRIENDS_SHARING
                assertEquals((if (isFriends) SharingVisibility.ALL else SharingVisibility.FRIENDS).name, choice.selectedOptionKey)
                assertEquals(UiText.Res(if (isFriends) Res.string.settings_privacy_all else Res.string.settings_privacy_friends), choice.value)
                assertTrue(choice.enabled)
            }
            assertEquals(UiText.Res(Res.string.settings_privacy_footer), fixture.viewModel.uiState.value.sections.single().footer)

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(SharingVisibility.ALL, SharingVisibility.NOBODY)
            )
            advanceUntilIdle()
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
            assertEquals(UiText.Res(Res.string.settings_privacy_all), fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).value)
            assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)
            assertEquals(UiText.Res(Res.string.settings_privacy_nobody), fixture.viewModel.choice(SettingRowId.SPORT_SHARING).value)
        }

    @Test
    fun privacyChoicesIndependentlySendTypedCommandsWithoutChangingOtherVisibility() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            advanceUntilIdle()
            fixture.viewModel.onChoiceChanged(SettingRowId.SCHEDULE_SHARING, SharingVisibility.ALL.name)
            advanceUntilIdle()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
            assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)

            fixture.viewModel.onChoiceChanged(SettingRowId.SPORT_SHARING, SharingVisibility.NOBODY.name)
            advanceUntilIdle()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertEquals(listOf(SharingVisibility.NOBODY), fixture.repository.sportSharingRequests)
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
            assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun privacyRejectsUnknownKeysAndOptionsUnchangedValuesAndObsoleteToggles() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            advanceUntilIdle()
            fixture.viewModel.onChoiceChanged(SettingRowId.VERSION, SharingVisibility.ALL.name)
            for (key in listOf(SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING)) {
                fixture.viewModel.onChoiceChanged(key, "unknown")
                fixture.viewModel.onChoiceChanged(key, "all")
                fixture.viewModel.onChoiceChanged(key, SharingVisibility.FRIENDS.name)
                fixture.viewModel.onToggleChanged(key, false)
            }
            advanceUntilIdle()
            assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
        }

    @Test
    fun privacyRejectsActionsDuringLoadingUnknownErrorDisabledAndUpdatingStates() =
        runTest(main.dispatcher) {
            val states = listOf(
                SharingSettingsState.Loading,
                SharingSettingsState.Error,
                SharingSettingsState.Disabled,
                SharingSettingsState.Content(SharingSettings(), updating = true)
            )
            states.forEach { sharing ->
                val fixture = createFixture(
                    page = SettingsPage.PRIVACY,
                    local = LocalSettings(customServicesEnabled = sharing != SharingSettingsState.Disabled),
                    sharing = sharing
                )
                advanceUntilIdle()
                for (key in listOf(SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING)) {
                    fixture.viewModel.onChoiceChanged(key, SharingVisibility.ALL.name)
                }
                advanceUntilIdle()
                assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
                assertTrue(fixture.repository.sportSharingRequests.isEmpty())
                if (sharing == SharingSettingsState.Loading) {
                    assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
                } else if (sharing !is SharingSettingsState.Content) {
                    fixture.viewModel.allItems().filterIsInstance<SettingItem.Choice>().forEach { choice ->
                        assertFalse(choice.enabled)
                        assertEquals(null, choice.selectedOptionKey)
                        assertEquals(UiText.Res(Res.string.settings_privacy_unknown), choice.value)
                    }
                }
            }
        }

    @Test
    fun privacyIgnoresChoicesOnOtherPagesAndWhileFreshSettingsAreMasked() =
        runTest(main.dispatcher) {
            for (page in listOf(SettingsPage.ROOT, SettingsPage.PRIVACY)) {
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(customServicesEnabled = true),
                    sharing = SharingSettingsState.Content(SharingSettings())
                )
                runCurrent()
                fixture.viewModel.onChoiceChanged(SettingRowId.SCHEDULE_SHARING, SharingVisibility.ALL.name)
                fixture.viewModel.onChoiceChanged(SettingRowId.SPORT_SHARING, SharingVisibility.ALL.name)
                advanceUntilIdle()
                assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
                assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            }
        }

    @Test
    fun pendingPrivacyCommandLocksBothRowsAndRejectsStaleDialogCallbacksUntilSaved() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            val saved = CompletableDeferred<Unit>()
            fixture.repository.sharingWrite = { saved.await() }
            advanceUntilIdle()
            fixture.viewModel.onChoiceChanged(SettingRowId.SCHEDULE_SHARING, SharingVisibility.ALL.name)
            // No collector has run yet: even a second callback in the same frame is rejected.
            fixture.viewModel.onChoiceChanged(SettingRowId.SPORT_SHARING, SharingVisibility.NOBODY.name)
            runCurrent()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            for (key in listOf(SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING)) {
                assertFalse(fixture.viewModel.choice(key).enabled)
                assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(key).selectedOptionKey)
                fixture.viewModel.onChoiceChanged(key, SharingVisibility.NOBODY.name)
            }
            runCurrent()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())

            saved.complete(Unit)
            advanceUntilIdle()
            assertTrue(fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).enabled)
            assertTrue(fixture.viewModel.choice(SettingRowId.SPORT_SHARING).enabled)
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
        }
}
