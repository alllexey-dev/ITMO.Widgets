import Shared
import UserNotifications

/// Routes a tapped notification by its `userInfo` (`NotificationTapRoutes`): a Backend push by the payload type of its
/// `data` envelope (a friendship event to the actor's profile, a sport booking to its lesson), a local notification
/// of `IosAppNotifier` by the destination it carries (the schedule changes, the recordbook or a subject, the BARS
/// sign-in), as Android's notification intents. The route goes to the shared route queue (`AppRouter.open(entry:)`),
/// which runs it once the session is ready, so a tap that launches the app routes as one on a running app does. A
/// notification that arrives in the foreground shows as a banner, as on Android.
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

    /// The route of a notification's `userInfo`; nil for a notification that names none.
    nonisolated static func entryRoute(userInfo: [AnyHashable: Any]) -> EntryRoute? {
        NotificationTapRoutes.shared.entryRoute(userInfo: userInfo)
    }

    func route(_ route: EntryRoute) {
        if let open {
            open(route)
        } else {
            waiting = route
        }
    }

    /// The completion-handler form, finished on the main queue: UIKit updates the app's snapshot in the handler and
    /// asserts it runs on the main thread. The `async` form returned on the cooperative pool and aborted the app on a
    /// tap that brought it back from the background (TestFlight 2.3.0 (20300)).
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let route = response.actionIdentifier == UNNotificationDefaultActionIdentifier
            ? Self.entryRoute(userInfo: response.notification.request.content.userInfo)
            : nil
        DispatchQueue.main.async {
            if let route {
                MainActor.assumeIsolated { self.route(route) }
            }
            completionHandler()
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        DispatchQueue.main.async { completionHandler([.banner, .list, .sound]) }
    }
}
