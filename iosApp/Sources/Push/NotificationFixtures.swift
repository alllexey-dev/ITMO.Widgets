#if DEBUG
import Foundation
import Shared
import UserNotifications

/// Debug-only notifications a UI test taps before the app has a push token: `-itmoNotificationFixture <kind>` asks
/// for permission, then posts one a moment after launch. `friendship` and `sport` are local notifications shaped like
/// Backend's pushes (`userInfo` with the `data` envelope and `recipient_isu`); `schedule-changes`, `marks` and
/// `bars-login` go through the app's own notifier (`IosAppNotifier`) with the `userInfo` a real one carries. Every
/// value is synthetic.
enum NotificationFixtures {
    static let argument = "itmoNotificationFixture"

    /// The pushes.
    enum Kind: String {
        case friendship
        case sport
    }

    /// The local notifications of the background checks.
    enum LocalKind: String {
        case scheduleChanges = "schedule-changes"
        case marks
        case barsLogin = "bars-login"
    }

    /// The fixture's `data` envelope, as Backend's payload contract spells it.
    static func data(_ kind: Kind) -> String {
        switch kind {
        case .friendship:
            #"{"type":"FRIENDSHIP_EVENT_PAYLOAD","payload":{"event":"REQUEST_RECEIVED","user":{"isu":100002},"#
                + #""occurredAt":"2026-10-05T12:00+03:00"}}"#
        case .sport:
            #"{"type":"SPORT_AUTO_SIGN_LESSONS_PAYLOAD","payload":{"sportLessons":[{"id":9001}]}}"#
        }
    }

    static func postIfRequested(_ defaults: UserDefaults = .standard) {
        guard let name = defaults.string(forKey: argument) else { return }
        if let kind = Kind(rawValue: name) {
            postOnceAllowed { try await UNUserNotificationCenter.current().add(request(kind)) }
        } else if let kind = LocalKind(rawValue: name) {
            postOnceAllowed { try await post(kind) }
        }
    }

    private static func postOnceAllowed(_ post: @escaping () async throws -> Void) {
        Task {
            let center = UNUserNotificationCenter.current()
            guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return }
            try? await post()
        }
    }

    private static func post(_ kind: LocalKind) async throws {
        switch kind {
        case .scheduleChanges: try await postFixtureScheduleChange()
        case .marks: try await postFixtureMarkDigest()
        case .barsLogin: try await postFixtureBarsPrompt()
        }
    }

    private static func request(_ kind: Kind) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        switch kind {
        case .friendship:
            content.title = AppStrings.string("notification_channel_friends")
        case .sport:
            content.title = AppStrings.string("notification_sport_success")
        }
        content.userInfo = ["data": data(kind), "recipient_isu": "100001"]
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 2, repeats: false)
        return UNNotificationRequest(identifier: "fixture.\(kind.rawValue)", content: content, trigger: trigger)
    }
}
#endif
