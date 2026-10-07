package dev.alllexey.itmowidgets.feature.update.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The update screen SwiftUI owns reads the offer the gate found from its Koin parameters. */
class UpdateIosModuleTest {

    @Test
    fun theScreenShowsTheOfferItIsOpenedWith() {
        for (unsupported in listOf(false, true)) {
            val update = AppUpdate(AppVersionName("2.3.0"), AppVersionName("2.4.0"), NOTE, unsupported)
            val handle = assertIs<SavedStateHandle>(AppUpdateIosParameters.viewModel(update).single())

            val model = AppUpdateViewModel(FakeAppUpdateRepository(update), handle)

            assertEquals(AppUpdateUiState("2.3.0", "2.4.0", NOTE, unsupported), model.uiState.value)
        }
    }

    private companion object {
        const val NOTE = "Синтетическая заметка"
    }
}
