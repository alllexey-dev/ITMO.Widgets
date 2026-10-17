import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The sign-in screen in all four appearances, as Android's `AuthScreen` references: first launch, an expired
/// session with a failed sign-in, the refresh-token sign-in running, the session still loading, and the
/// `auth_login_refresh_token` sheet. `AuthContent` is rendered directly, without the ViewModel, in a window, so the glass
/// button draws as on screen.
@MainActor
final class AuthSnapshotTests: XCTestCase {
    func testInitial() {
        assertScreenAppearances(of: content(AuthFixtures.state()), named: "initial")
    }

    func testOtherWays() {
        let screen = content(AuthFixtures.state()).sheet(isPresented: .constant(true)) {
            AuthOtherWaysSheet(signIn: { _ in })
        }
        assertScreenAppearances(of: screen, named: "other-ways", appearances: [.light, .dark], settle: 1.5)
    }

    func testReauthenticationWithError() {
        let state = AuthFixtures.state(
            reauthenticationRequired: true,
            error: UiTextDynamic(value: AppStrings.string("auth_error_network"))
        )
        assertScreenAppearances(of: content(state), named: "reauthentication-error")
    }

    func testSigningIn() {
        let state = AuthFixtures.state(manualLoginInProgress: true)
        assertScreenAppearances(of: content(state), named: "signing-in", appearances: [.light, .dark])
    }

    func testInitializing() {
        assertAppearances(of: content(AuthFixtures.state(initializing: true)), named: "initializing", appearances: [.light, .dark], height: 400)
    }

    private func content(_ state: AuthUiState) -> some View {
        AuthContent(state: state, logoTapped: {}, signIn: {}, signInWithRefreshToken: {})
    }
}

enum AuthFixtures {
    static func state(
        initializing: Bool = false,
        reauthenticationRequired: Bool = false,
        manualLoginInProgress: Bool = false,
        error: UiText? = nil
    ) -> AuthUiState {
        AuthUiState(
            initializing: initializing,
            sessionTransitionInProgress: false,
            reauthenticationRequired: reauthenticationRequired,
            manualLoginInProgress: manualLoginInProgress,
            error: error
        )
    }
}
