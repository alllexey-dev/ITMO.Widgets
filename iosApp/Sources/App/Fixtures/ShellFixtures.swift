import Foundation
import Shared
import SwiftUI

/// Fixture mode of the shell (IO-06b): in a Debug build a fixture session gate and demo banner without the shared
/// session. Every tab root is its Compose screen since IO-09d2 hosted the recordbook, the last placeholder root.
enum ShellFixtures {
    /// The launch argument that picks a fixture session instead of the shared one:
    /// `-itmoShellSession loading|signed-out|demo|signed-in`.
    static let sessionArgument = "itmoShellSession"

    /// The fixture session from the launch arguments; nil without one, or in a Release build.
    static func sessionState(_ defaults: UserDefaults = .standard) -> ShellSessionState? {
        #if DEBUG
        defaults.string(forKey: sessionArgument).flatMap(ShellSessionState.init(rawValue:))
        #else
        nil
        #endif
    }
}

/// The signed-out gate of a fixture session; the shared session shows `ItmoSignInScreen`.
struct FixtureSignInGate: View {
    let signIn: () -> Void

    var body: some View {
        VStack(spacing: ItmoSpacing.group) {
            Spacer(minLength: 0)
            ItmoEmptyView(symbol: .login, title: AppStrings.string("auth_title"))
            ItmoProgressButton(title: AppStrings.string("auth_sign_in"), action: signIn)
                .padding(.horizontal, ItmoSpacing.screenMargin)
                .accessibilityIdentifier("gate.signIn")
            Spacer(minLength: 0)
        }
        .padding(.bottom, ItmoSpacing.section)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.gate.signedOut")
    }
}
