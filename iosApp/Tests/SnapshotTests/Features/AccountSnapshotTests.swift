import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The web sign-in sheet, the update offer and the My ITMO page's chrome (IO-08b) in all four appearances, one
/// reference per state Android's references cover. The content views are rendered directly, without ViewModels; the
/// browser card comes from the fixture ViewModel (`WebLoginIosFixture`), whose texts are the shared ones.
@MainActor
final class AccountSnapshotTests: XCTestCase {
    private let pageHeight: CGFloat = 480

    // MARK: Web sign-in

    func testWebLoginInput() {
        assertAppearances(of: webLogin(WebLoginUiStateInput(code: "", error: nil)), named: "web-login-input")
    }

    func testWebLoginInputError() {
        let state = WebLoginUiStateInput(
            code: "ABCD-234",
            error: UiTextDynamic(value: AppStrings.string("web_login_code_invalid"))
        )
        assertAppearances(of: webLogin(state, code: "ABCD-234"), named: "web-login-input-error")
    }

    func testWebLoginChecking() {
        assertAppearances(
            of: webLogin(WebLoginUiStateChecking(code: "ABCD2345"), code: "ABCD2345"),
            named: "web-login-checking"
        )
    }

    func testWebLoginConfirm() async {
        let confirm = await fixtureConfirm()
        assertAppearances(of: webLogin(confirm), named: "web-login-confirm")
    }

    func testWebLoginApproving() async {
        let confirm = await fixtureConfirm()
        let approving = WebLoginUiStateConfirm(
            code: confirm.code,
            preview: confirm.preview,
            browser: confirm.browser,
            requestedAt: confirm.requestedAt,
            approving: true
        )
        assertAppearances(of: webLogin(approving), named: "web-login-approving")
    }

    func testWebLoginDone() {
        assertAppearances(of: webLogin(WebLoginUiStateDone.shared), named: "web-login-done")
    }

    func testWebLoginError() {
        let state = WebLoginUiStateError(
            text: UiTextDynamic(value: AppStrings.string("web_login_services_disabled")),
            code: ""
        )
        assertAppearances(of: webLogin(state), named: "web-login-error")
    }

    // MARK: Update offer

    func testUpdateSupportedWithNote() {
        let state = AppUpdateUiState(
            installed: "2.3.0",
            latest: "2.4.0",
            note: "Синтетическая заметка о версии: новые виджеты.",
            unsupported: false
        )
        assertAppearances(of: AppUpdateContent(state: state), named: "update-supported")
    }

    func testUpdateUnsupported() {
        let state = AppUpdateUiState(installed: "2.3.0", latest: "2.5.0", note: "", unsupported: true)
        assertAppearances(of: AppUpdateContent(state: state), named: "update-unsupported")
    }

    // MARK: My ITMO

    func testMyItmoWebLoading() {
        let page = MyItmoWebContent(load: .loading, progress: 0.4, retry: {}) {
            Color(uiColor: .secondarySystemBackground)
        }
        assertAppearances(of: page, named: "my-itmo-loading", appearances: [.light, .dark], height: pageHeight)
    }

    func testMyItmoWebFailed() {
        let page = MyItmoWebContent(load: .failed, retry: {}) {
            Color(uiColor: .secondarySystemBackground)
        }
        assertAppearances(of: page, named: "my-itmo-failed", height: pageHeight)
    }

    // MARK: Helpers

    private func webLogin(_ state: WebLoginUiState, code: String = "") -> some View {
        WebLoginContent(state: state, code: .constant(code))
    }

    /// The fixture's browser card: Chrome on macOS, requested at 12:04 Moscow time.
    private func fixtureConfirm() async -> WebLoginUiStateConfirm {
        guard let time = IosKoin.shared.get(protocol: AcademicTimeProvider.self) as? AcademicTimeProvider else {
            preconditionFailure("Koin resolved no AcademicTimeProvider")
        }
        let model = WebLoginIosFixture.shared.viewModel(time: time)
        model.onScanned(raw: WebLoginIosFixture.shared.LINK)
        let deadline = Date().addingTimeInterval(5)
        while !(model.uiState.value is WebLoginUiStateConfirm), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        guard let confirm = model.uiState.value as? WebLoginUiStateConfirm else {
            preconditionFailure("the fixture code was not confirmed")
        }
        return confirm
    }
}
