package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `presents stored hide flags as positive toggles`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(
                SportDisplaySettings(hideTeacherSelector = true, hideTimeSelector = false)
            )

            advanceUntilIdle()

            assertEquals(false, toggle(viewModel, SettingsViewModel.KEY_SPORT_TEACHER_FILTER).checked)
            assertEquals(true, toggle(viewModel, SettingsViewModel.KEY_SPORT_TIME_FILTER).checked)
        }

    @Test
    fun `enabling a toggle clears the stored hide flag`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSettingsRepository(
                SportDisplaySettings(hideTeacherSelector = true, hideTimeSelector = true)
            )
            val viewModel = SettingsViewModel(repository, AppVersion("2.0.1"))
            advanceUntilIdle()

            viewModel.onToggleChanged(SettingsViewModel.KEY_SPORT_TEACHER_FILTER, true)
            advanceUntilIdle()

            assertEquals(false, repository.settings.value.hideTeacherSelector)
            assertEquals(
                true,
                toggle(viewModel, SettingsViewModel.KEY_SPORT_TEACHER_FILTER).checked
            )
        }

    @Test
    fun `exposes the version as a read-only fact`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(SportDisplaySettings())

            advanceUntilIdle()

            val info = viewModel.sections
                .value
                .flatMap(SettingSection::items)
                .filterIsInstance<SettingItem.Info>()
                .single()

            assertEquals(UiText.Dynamic("2.0.1"), info.value)
        }

    @Test
    fun `groups every section under a title`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(SportDisplaySettings())

            advanceUntilIdle()

            val sections = viewModel.sections.value
            assertEquals(
                listOf(
                    UiText.Resource(R.string.settings_group_sport),
                    UiText.Resource(R.string.settings_group_about)
                ),
                sections.map(SettingSection::title)
            )
            assertTrue(sections.all { it.items.isNotEmpty() })
        }

    private fun createViewModel(sport: SportDisplaySettings): SettingsViewModel {
        return SettingsViewModel(FakeSettingsRepository(sport), AppVersion("2.0.1"))
    }

    private fun toggle(viewModel: SettingsViewModel, key: String): SettingItem.Toggle {
        return viewModel.sections
            .value
            .flatMap(SettingSection::items)
            .filterIsInstance<SettingItem.Toggle>()
            .single { it.key == key }
    }

    private class FakeSettingsRepository(
        initial: SportDisplaySettings
    ) : SettingsRepository {
        val settings = MutableStateFlow(initial)

        override fun observeSportDisplaySettings(): Flow<SportDisplaySettings> = settings

        override suspend fun setTeacherSelectorHidden(hidden: Boolean) {
            settings.value = settings.value.copy(hideTeacherSelector = hidden)
        }

        override suspend fun setTimeSelectorHidden(hidden: Boolean) {
            settings.value = settings.value.copy(hideTimeSelector = hidden)
        }
    }
}
