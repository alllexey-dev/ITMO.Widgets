import Shared
import UserNotifications

/// Routes a tapped notification by the payload type of its `data` envelope (`NotificationTapRoutes`): a friendship
/// event to the actor's profile, a sport booking to its lesson. The route goes to the shared route queue
/// (`AppRouter.open(entry:)`), which runs it once the session is ready. A notification that arrives in the
/// foreground shows as a banner, as on Android.
@MainActor
final class NotificationTaps: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationTaps()

    private var open: ((EntryRoute) -> Void)?
    private var waiting: EntryRoute?

    /// Hands the routes to `open`; the route of a tap that launched the app waits until then.
    func attach(_ open: @escaping (EntryRoute) -> Void) {
        self.open = open
        if let waiting {
            self.waiting = nil
            open(waiting)
        }
    }

    /// The route of a notification's `userInfo`; nil for a notification that is not a Backend push.
    nonisolated static func entryRoute(userInfo: [AnyHashable: Any]) -> EntryRoute? {
        NotificationTapRoutes.shared.entryRoute(data: userInfo[NotificationTapRoutes.shared.DATA_KEY] as? String)
    }

    func route(_ route: EntryRoute) {
        if let open {
            open(route)
        } else {
            waiting = route
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let route = Self.entryRoute(userInfo: response.notification.request.content.userInfo) else { return }
        await self.route(route)
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
