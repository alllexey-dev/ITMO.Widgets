package dev.alllexey.itmowidgets.feature.weblogin.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.weblogin.data.PairingFixtureRepository
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginFixtureRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffToken
import dev.alllexey.itmowidgets.feature.weblogin.domain.HpkeSender
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingHandoff
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingSealer
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Koin parameters of the web sign-in's ViewModel when SwiftUI owns it:
 * `ScreenViewModelStore` has no saved-state registry, so the sheet gets a fresh `SavedStateHandle` and a reopened
 * sheet starts with an empty field. Swift passes them to `store.resolve(type: WebLoginViewModel.self, parameters:)`;
 * Core 2.0's users area of the repository comes from `iosCoreModule`.
 */
object WebLoginIosParameters {

    fun viewModel(): List<Any> = listOf(SavedStateHandle())
}

/**
 * The web sign-in of a Debug build launched with `-itmoWebLoginFixture` (UI tests): the simulator has no camera, so
 * the scanner hands over [LINK] and the field takes [CODE]; [WebLoginFixtureRepository] and [PairingFixtureRepository]
 * answer without Backend.
 */
object WebLoginIosFixture {
    const val CODE = WebLoginFixtureRepository.CODE

    /** What the QR on the web version holds for [CODE]. */
    const val LINK = "https://dev.widgets.alllexey.dev/app/login?code=$CODE"

    /** A pairing link of [PairingFixtureRepository]'s agent. */
    const val PAIR_LINK = PairingFixtureRepository.LINK

    /** A screen lock the fixture passes at once. */
    val unlock: DeviceUnlock = DeviceUnlock { DeviceUnlockResult.UNLOCKED }

    fun viewModel(time: AcademicTimeProvider): WebLoginViewModel =
        WebLoginViewModel(SavedStateHandle(), WebLoginFixtureRepository(), PairingFixtureRepository(), handoff(), time)

    /** A hand-off of a synthetic token sealed to nothing: the fixture never seals a real session. */
    private fun handoff() = PairingHandoff(
        session = { AppResult.Success(HandoffToken("fixture-refresh-token", Instant.parse("2026-12-01T00:00:00Z"))) },
        sealer = PairingSealer(HpkeSender { _, _, plaintext -> ByteArray(SEALED_OVERHEAD + plaintext.size) }),
        users = object : CurrentUserProvider {
            override suspend fun getCurrentUser() = CurrentUser(isu = 100001, name = null, pictureUrl = null)
        },
        clock = Clock.System,
    )

    private const val SEALED_OVERHEAD = 48
}
