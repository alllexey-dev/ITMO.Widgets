import Foundation
import Shared
import SwiftUI

/// Fixture mode of the shell (IO-06b): placeholder roots, and in a Debug build a fixture session gate and demo
/// banner without Kotlin. The placeholders stand in for the CMP screens the feature cards host (IO-09x; the QR pass is
/// CMP since IO-21, the home feed since IO-09a, the sport tab since IO-09c, the Me tab since IO-09e, the schedule
/// since IO-09b), so they draw their own top bar under the compose chrome.
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

/// A tab root: the tab's title in its own top bar and the tab's empty state, until the tab's IO card hosts its
/// Compose root.
struct FixtureRootScreen: View {
    let tab: ShellTab

    var body: some View {
        FixtureComposeScreen(title: tab.title) {
            ItmoEmptyView(symbol: tab.symbol, title: tab.title)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.\(tab.rawValue)")
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

/// A CMP tab root's layout in SwiftUI: its own top bar over scrolling content.
struct FixtureComposeScreen<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: ItmoSpacing.related) {
                Text(verbatim: title)
                    .font(.itmo(.headlineSmall))
                    .lineLimit(2)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, ItmoSpacing.screenMargin)
            .frame(minHeight: 64)
            ScrollView {
                VStack(spacing: ItmoSpacing.group) {
                    content
                }
            }
        }
        .background(Color(uiColor: .systemBackground))
    }
}
