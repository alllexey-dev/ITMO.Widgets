@testable import ITMOWidgets
import Shared
import UIKit
import XCTest

/// The push plumbing of the app process before the Apple account (IO-13a): the graph binds one shared push
/// registration (`PushDeviceRegistration`, whose Backend cases run on a MockEngine in `PushDeviceRegistrationTest`),
/// the device has no token yet so nothing is registered, and a tapped notification reaches the shared route queue by
/// its payload type. Every ISU and lesson is synthetic.
@MainActor
final class DeviceRegistrationTests: XCTestCase {
    // MARK: Registration

    func testTheGraphBindsOneSharedPushRegistration() throws {
        let tokenSync = try XCTUnwrap(IosKoin.shared.get(protocol: FcmTokenSync.self) as? PushDeviceRegistration)
        let devices = try XCTUnwrap(IosKoin.shared.get(protocol: BackendDeviceSession.self) as? PushDeviceRegistration)
        XCTAssertTrue(tokenSync === devices)
        XCTAssertTrue(IosKoin.shared.get(type: PushForegroundRefresh.self) is PushForegroundRefresh)
    }

    func testTheDeviceNamesTheModelAndTheAppVersionButNoPersonalName() throws {
        let device = try XCTUnwrap(IosKoin.shared.get(type: IosPushDevice.self) as? IosPushDevice)

        XCTAssertEqual(device.name, "Apple \(UIDevice.current.model)")
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        XCTAssertEqual(device.appVersion, version)
    }

    /// The app's own foreground refresh has run by now; without a token it recorded no registration. The refresh is
    /// not called again here: the first notification settings read of a fresh simulator can take two minutes.
    func testWithoutAPushTokenNothingIsRegistered() async throws {
        let device = try XCTUnwrap(IosKoin.shared.get(type: IosPushDevice.self) as? IosPushDevice)
        let registrations = try XCTUnwrap(
            IosKoin.shared.get(type: PushRegistrationPreferences.self) as? PushRegistrationPreferences
        )

        let token = try await device.token()
        XCTAssertNil(token, "no APNs or FCM registration before IO-13b")
        let registration = try await registrations.get()
        XCTAssertNil(registration)
    }

    // MARK: Taps

    func testAFriendshipTapOpensTheMeTabForTheActorsProfile() throws {
        let route = NotificationTaps.entryRoute(userInfo: userInfo(.friendship))

        XCTAssertEqual(route, EntryRoute(
            tab: .me, overlay: AppRoutes.UserProfile(isu: 100_002), request: nil, activity: nil, alert: nil,
            shortcutId: nil
        ))
        let router = readyRouter()
        XCTAssertTrue(router.open(entry: try XCTUnwrap(route)))
        XCTAssertEqual(router.selectedTab, .me)
    }

    func testASportTapOpensItsLessonOnTheSportTab() throws {
        let router = readyRouter()
        let taps = NotificationTaps()
        taps.attach { route in router.open(entry: route) }

        taps.route(try XCTUnwrap(NotificationTaps.entryRoute(userInfo: userInfo(.sport))))

        XCTAssertEqual(router.selectedTab, .sport)
        XCTAssertEqual(
            router.consumeRequest(of: .sport) as? TabRequestSportLesson,
            TabRequestSportLesson(lessonId: 9001, predicted: false)
        )
    }

    func testATapBeforeTheRouterIsAttachedWaitsForIt() throws {
        let taps = NotificationTaps()
        taps.route(try XCTUnwrap(NotificationTaps.entryRoute(userInfo: userInfo(.sport))))
        let router = readyRouter()

        taps.attach { route in router.open(entry: route) }

        XCTAssertEqual(router.selectedTab, .sport)
    }

    func testANotificationThatIsNotABackendPushOpensNothing() {
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: [:]))
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: ["data": "not json"]))
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: ["data": #"{"type":"SOMETHING_NEW","payload":{}}"#]))
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: ["data": 42]))
    }

    private func userInfo(_ kind: NotificationFixtures.Kind) -> [AnyHashable: Any] {
        ["data": NotificationFixtures.data(kind), "recipient_isu": "100001"]
    }

    private func readyRouter() -> AppRouter {
        let router = AppRouter()
        router.sessionChanged(ShellSessionState.signedIn.sessionState, onboarding: .passed)
        router.shellMounted(true)
        return router
    }
}
