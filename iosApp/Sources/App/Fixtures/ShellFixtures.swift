import Foundation
import Shared
import SwiftUI

/// Fixture mode of the shell (IO-06b): placeholder roots, the session gate and the demo banner without Kotlin. The
/// placeholders stand in for the CMP screens the feature cards host (IO-21 wires the QR pass, IO-09x the rest), so
/// they draw their own top bar under the compose chrome.
enum ShellFixtures {
    /// The launch argument that picks the fixture session: `-itmoShellSession loading|signed-out|demo|signed-in`.
    static let sessionArgument = "itmoShellSession"

    /// The session from the launch arguments, signed in without one.
    static func sessionState(_ defaults: UserDefaults = .standard) -> ShellSessionState {
        defaults.string(forKey: sessionArgument).flatMap(ShellSessionState.init(rawValue:)) ?? .signedIn
    }
}

/// A tab root: the tab's title in its own top bar and the tab's empty state; home has the QR entry IO-21 wires.
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

/// The QR pass placeholder above home, with the back button of a CMP top bar.
struct FixtureQrPassScreen: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        FixtureComposeScreen(title: AppStrings.string("qr_pass_title"), back: { dismiss() }) {
            ItmoEmptyView(
                symbol: .qrCode,
                title: AppStrings.string("qr_pass_title"),
                description: AppStrings.string("qr_pass_description")
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("qr.pass")
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

/// A CMP screen's layout in SwiftUI: its own top bar (a back button above the root) over scrolling content.
struct FixtureComposeScreen<Content: View>: View {
    let title: String
    var back: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: ItmoSpacing.related) {
                if let back {
                    Button(action: back) {
                        AppSymbol.arrowBack.image
                            .frame(width: ItmoMetrics.touchTarget, height: ItmoMetrics.touchTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("common_back")))
                    .accessibilityIdentifier("topBar.back")
                }
                Text(verbatim: title)
                    .font(.itmo(back == nil ? .headlineSmall : .titleLarge))
                    .lineLimit(2)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
            }
            .padding(.leading, back == nil ? ItmoSpacing.screenMargin : ItmoSpacing.related)
            .padding(.trailing, ItmoSpacing.screenMargin)
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
