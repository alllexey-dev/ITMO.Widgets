package dev.alllexey.itmowidgets.feature.weblogin.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.weblogin.domain.WebLoginCode
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.web_login_not_found
import dev.alllexey.itmowidgets.shared.feature.account.web_login_requested_at
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The web sign-in fixture of iOS's UI tests: the scanner's link and the typed code reach the same approval. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebLoginIosModuleTest {
    private val main = TestMainDispatcher()
    private val time = FixedAcademicTime()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theFixtureLinkIsASignInLinkForTheFixtureCode() {
        assertEquals(WebLoginIosFixture.CODE, WebLoginCode.parse(WebLoginIosFixture.LINK))
    }

    @Test
    fun theScannedFixtureLinkIsConfirmedThenApproved() = runTest(main.dispatcher) {
        val model = WebLoginIosFixture.viewModel(time)

        model.onScanned(WebLoginIosFixture.LINK)
        runCurrent()

        val confirm = assertIs<WebLoginUiState.Confirm>(model.uiState.value)
        assertEquals(UiText.Res(Res.string.web_login_requested_at, listOf("12:04")), confirm.requestedAt)
        model.approve()
        runCurrent()
        assertEquals(WebLoginUiState.Done, model.uiState.value)
    }

    @Test
    fun anyOtherTypedCodeIsUnknownInPlace() = runTest(main.dispatcher) {
        val model = WebLoginIosFixture.viewModel(time)

        model.onCodeChanged("ABCD2346")
        model.submit()
        runCurrent()

        assertEquals(
            WebLoginUiState.Input("ABCD2346", UiText.Res(Res.string.web_login_not_found)),
            model.uiState.value,
        )
    }

    @Test
    fun theSheetParametersAreOneEmptySavedStateHandle() {
        val parameters = WebLoginIosParameters.viewModel()

        assertEquals(1, parameters.size)
        assertEquals(emptySet(), assertIs<SavedStateHandle>(parameters.single()).keys())
    }
}
