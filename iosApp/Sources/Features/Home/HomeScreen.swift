import Shared
import SwiftUI
import UIKit
import UserNotifications

/// The home tab's root: LH-2's Compose feed (`homeViewController`, IO-09a) with the router behind its cards and
/// buttons. What only iOS does stays here: the widget hint opens `WidgetHowToSheet` (iOS lets no app place a widget),
/// the notification hint asks iOS once and then opens the app's notification settings, and a key the demo refuses
/// says `error_demo_unavailable`, as Android's shell does. The feed re-checks its hints whenever the app becomes
/// active again.
struct HomeScreen: View {
    let router: AppRouter
    @State private var host = HomeHost()

    var body: some View {
        ComposeHost { [router, host] in
            #if DEBUG
            HomeHost.forgetClosedHintsIfRequested()
            #endif
            return homeViewController(
                open: { route in host.opened(router.open(route)) },
                showWidgetHowTo: { host.showsWidgetHowTo = true },
                requestNotifications: { Task { await host.requestNotifications() } }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.home")
        .overlay(alignment: .bottom) {
            if let message = host.message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: .seconds(3))
                        if host.message?.id == message.id { host.message = nil }
                    }
            }
        }
        .animation(.default, value: host.message?.id)
        .sheet(isPresented: Binding(get: { host.showsWidgetHowTo }, set: { host.showsWidgetHowTo = $0 })) {
            WidgetHowToSheet()
        }
    }
}

/// The state the home host keeps beside the Compose feed.
@MainActor
@Observable
final class HomeHost {
    var showsWidgetHowTo = false
    var message: SettingsMessage?

    /// The answer of the router to a card or button of the feed.
    func opened(_ opening: RouteOpening) {
        guard opening == .refusedInDemo else { return }
        let text = AppStrings.string("error_demo_unavailable")
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }

    /// Asks once while iOS has never asked; after that only Settings can change the answer.
    func requestNotifications() async {
        let center = UNUserNotificationCenter.current()
        if await center.notificationSettings().authorizationStatus == .notDetermined {
            _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        } else if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
            await UIApplication.shared.open(url)
        }
    }

    #if DEBUG
    /// The launch argument of a Debug build that brings the closed hints back, so a UI test starts from every hint
    /// the device state shows.
    static let forgetHintsArgument = "-itmoForgetHomeHints"

    private static var forgotHints = false

    static func forgetClosedHintsIfRequested(arguments: [String] = CommandLine.arguments) {
        guard !forgotHints, arguments.contains(forgetHintsArgument) else { return }
        forgotHints = true
        forgetClosedHomeHints()
    }
    #endif
}

/// What the widget hint's action opens on iOS: how to add a widget from the home screen, the text of the first-run
/// widget steps.
struct WidgetHowToSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
                    AppSymbol.widgets.image
                        .font(.itmo(.bodyLarge))
                        .foregroundStyle(ItmoColor.primary)
                        .accessibilityHidden(true)
                    Text(verbatim: AppStrings.string("ios_onboarding_widget_howto_text"))
                        .font(.itmo(.bodyLarge))
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(ItmoSpacing.screenMargin)
            }
            .navigationTitle(Text(verbatim: AppStrings.string("ios_onboarding_widget_howto_title")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        AppSymbol.close.image
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                    .accessibilityIdentifier("sheet.close")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .accessibilityIdentifier("home.widgetHowTo")
    }
}
