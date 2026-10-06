import Foundation
import Shared
import SwiftUI

/// Fixture mode of the shell (IO-06b): placeholder roots, the session gate and the demo banner without Kotlin. The
/// placeholders stand in for the CMP screens the feature cards host (IO-09x; the QR pass is CMP since IO-21), so
/// they draw their own top bar under the compose chrome.
enum ShellFixtures {
    /// The launch argument that picks the fixture session: `-itmoShellSession loading|signed-out|demo|signed-in`.
    static let sessionArgument = "itmoShellSession"

    /// The session from the launch arguments, signed in without one.
    static func sessionState(_ defaults: UserDefaults = .standard) -> ShellSessionState {
        defaults.string(forKey: sessionArgument).flatMap(ShellSessionState.init(rawValue:)) ?? .signedIn
    }
}

/// A tab root: the tab's title in its own top bar and the tab's empty state; home has the entry to the QR pass.
struct FixtureRootScreen: View {
    let tab: ShellTab
    let router: AppRouter

    var body: some View {
        FixtureComposeScreen(title: tab.title) {
            ItmoEmptyView(symbol: tab.symbol, title: tab.title)
            if tab == .home {
                ItmoProgressButton(title: AppStrings.string("home_open_qr"), symbol: .qrCode) {
                    router.open(AppRoutes.QrPass.shared)
                }
                .padding(.horizontal, ItmoSpacing.screenMargin)
                .accessibilityIdentifier("home.openQr")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.\(tab.rawValue)")
        // The schedule root marks each `today` route, so UI tests can see it arrive.
        .accessibilityValue(tab == .schedule && router.todayRequest > 0 ? "today-\(router.todayRequest)" : "")
    }
}

/// The signed-out gate: IO-07 replaces it with the sign-in screen.
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
