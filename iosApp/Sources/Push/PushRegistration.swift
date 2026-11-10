import FirebaseCore
import FirebaseMessaging
import Shared
import UIKit

/// The push token of this installation. Backend sends through FCM, which reaches iOS through APNs
/// (`PushMessageFactory` in Backend), so the app registers with APNs, hands the device token to Firebase Messaging and
/// gives the FCM token it gets back to the shared `IosPushDevice`; `PushRefresh` registers it with Backend.
///
/// Firebase starts only with `GoogleService-Info.plist` in the bundle (the iOS app of the Firebase project). A build
/// without it, or without the `aps-environment` entitlement (unsigned and re-signed builds), gets no token and
/// registers nothing. Asking for APNs shows no prompt: whether alerts show is the notification permission, which
/// Backend learns separately (`alertsAllowed`). Method swizzling is off (`FirebaseAppDelegateProxyEnabled`), so the
/// app delegate passes the APNs token itself and `NotificationTaps` stays the notification center's delegate.
@MainActor
final class PushRegistration: NSObject, MessagingDelegate {
    static let shared = PushRegistration()

    /// The Firebase options of this build; nil without `GoogleService-Info.plist`.
    nonisolated static func firebaseOptions(bundle: Bundle = .main) -> FirebaseOptions? {
        guard let path = bundle.path(forResource: "GoogleService-Info", ofType: "plist") else { return nil }
        return FirebaseOptions(contentsOfFile: path)
    }

    private var started = false

    /// XCTest sets it in the app it hosts tests in.
    private nonisolated static var hostsUnitTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    /// Run once at launch by the app delegate. The host of the unit tests never starts it, so they see no token.
    func start(_ application: UIApplication) {
        guard !started, !Self.hostsUnitTests, let options = Self.firebaseOptions() else { return }
        started = true
        FirebaseApp.configure(options: options)
        Messaging.messaging().delegate = self
        application.registerForRemoteNotifications()
    }

    /// The APNs device token, from `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`.
    func didRegister(deviceToken: Data) {
        guard started else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    /// The current FCM token; nil before Firebase has one.
    var fcmToken: String? {
        started ? Messaging.messaging().fcmToken : nil
    }

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Task { @MainActor in
            PushRefresh.updateToken(fcmToken)
            await PushRefresh.run()
        }
    }
}
