#if DEBUG
import Foundation
import UserNotifications

/// Debug-only local notifications shaped like Backend's pushes (`userInfo` with the `data` envelope and
/// `recipient_isu`), so a UI test taps a real notification before the app has a push token:
/// `-itmoNotificationFixture friendship|sport` asks for permission, then posts one a moment after launch. Every
/// value is synthetic.
enum NotificationFixtures {
    static let argument = "itmoNotificationFixture"

    enum Kind: String {
        case friendship
        case sport
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
        guard let kind = defaults.string(forKey: argument).flatMap(Kind.init(rawValue:)) else { return }
        Task {
            let center = UNUserNotificationCenter.current()
            guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return }
            try? await center.add(request(kind))
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
