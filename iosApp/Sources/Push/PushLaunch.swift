import UIKit
import UserNotifications

/// The launch step of the push plumbing, run by the app's one delegate (`ITMOWidgetsAppDelegate`, which also
/// takes the quick actions) when launch finishes: it makes `NotificationTaps` the notification center's delegate
/// before launch ends, so the tap that launched the app reaches the router, and starts the push token
/// (`PushRegistration`).
enum PushLaunch {
    @MainActor
    static func prepare(_ application: UIApplication) {
        UNUserNotificationCenter.current().delegate = NotificationTaps.shared
        PushRegistration.shared.start(application)
    }
}
