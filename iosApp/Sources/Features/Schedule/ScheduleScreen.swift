import Shared
import SwiftUI
import UIKit

/// The schedule tab's root: L10's Compose schedule (`scheduleRootScreen`, IO-09b) with the router behind its lessons,
/// its queued sport rows and its friends button. The friend picker answers through the router
/// (`open(_:onResult:)`); each `today` entry route (the widget, the quick action, the App Shortcut) puts the own
/// schedule back on today.
struct ScheduleScreen: View {
    let router: AppRouter
    @State private var host = ScheduleHost()

    var body: some View {
        ComposeHost { [router, host] in
            host.make(router: router)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.schedule")
        .onChange(of: router.todayRequest) {
            host.showToday()
        }
        .scheduleMessages(host.messages)
    }
}

/// What the schedule root keeps beside its Compose screen: the Kotlin root it sends requests to.
@MainActor
@Observable
final class ScheduleHost {
    let messages = ScheduleMessages()
    @ObservationIgnored private var root: ScheduleRootScreen?

    func make(router: AppRouter) -> UIViewController {
        let messages = messages
        let root = scheduleRootScreen(
            open: { route in messages.opened(router.open(route)) },
            pickFriend: { [weak self] shown in self?.pickFriend(shown: shown.int32Value, router: router) }
        )
        self.root = root
        return root.controller
    }

    func showToday() {
        root?.showToday()
    }

    /// The picker for the ISU shown now; its answer comes back once.
    private func pickFriend(shown: Int32, router: AppRouter) {
        let picker = AppRoutes.FriendSelector(selectedIsu: shown)
        let opening = router.open(picker) { [weak self] (pick: FriendPick) in
            self?.root?.showFriend(user: pick.user)
        }
        messages.opened(opening)
    }
}

/// The friend picker's answer to the schedule root: the person, or `nil` for the own schedule.
struct FriendPick {
    let user: UserSummary?
}

/// What the schedule's hosts say beside their Compose content, as Android's toasts and snackbars do: a key the demo
/// refuses, a map or a link no app takes.
@MainActor
@Observable
final class ScheduleMessages {
    var message: SettingsMessage?

    /// The answer of the router to a row or button of the content.
    func opened(_ opening: RouteOpening) {
        guard opening == .refusedInDemo else { return }
        say("error_demo_unavailable")
    }

    /// The catalog string `key`, read aloud too.
    func say(_ key: String) {
        let text = AppStrings.string(key)
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}

extension View {
    /// The message of `messages` at the bottom for a few seconds.
    func scheduleMessages(_ messages: ScheduleMessages) -> some View {
        overlay(alignment: .bottom) {
            if let message = messages.message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: .seconds(3))
                        if messages.message?.id == message.id { messages.message = nil }
                    }
            }
        }
        .animation(.default, value: messages.message?.id)
    }
}
