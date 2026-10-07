import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The sign-in screen (IO-07b) in all four appearances, as Android's `AuthScreen` references: first launch, an expired
/// session with a failed sign-in, the refresh-token sign-in running, and the session still loading. `AuthContent` is
/// rendered directly, without the ViewModel.
@MainActor
final class AuthSnapshotTests: XCTestCase {
    private let screenHeight: CGFloat = 640

    func testInitial() {
        assertAppearances(of: content(AuthFixtures.state()), named: "initial", height: screenHeight)
    }

    func testReauthenticationWithError() {
        let state = AuthFixtures.state(
            reauthenticationRequired: true,
            error: UiTextDynamic(value: AppStrings.string("auth_error_network"))
        )
        assertAppearances(of: content(state), named: "reauthentication-error", height: screenHeight)
    }

    func testSigningIn() {
        let state = AuthFixtures.state(manualLoginInProgress: true)
        assertAppearances(of: content(state), named: "signing-in", height: screenHeight)
    }

    func testInitializing() {
        assertAppearances(of: content(AuthFixtures.state(initializing: true)), named: "initializing", height: 400)
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
