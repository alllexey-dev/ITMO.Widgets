import UIKit
import UserNotifications

/// The launch step of the push plumbing (IO-13a) on the app's one delegate (`ITMOWidgetsAppDelegate`, which also
/// takes the quick actions): it makes `NotificationTaps` the notification center's delegate before launch ends, so
/// the tap that launched the app reaches the router. IO-13b adds the APNs callbacks here.
extension ITMOWidgetsAppDelegate {
    @objc func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = NotificationTaps.shared
        #if DEBUG
        NotificationFixtures.postIfRequested()
        #endif
        return true
    }
}
