@testable import ITMOWidgets
import Shared
import UIKit
import XCTest

/// The push plumbing of the app process before the Apple account (IO-13a): the graph binds one shared push
/// registration (`PushDeviceRegistration`, whose Backend cases run on a MockEngine in `PushDeviceRegistrationTest`),
/// the device has no token yet so nothing is registered, and a tapped notification reaches the shared route queue: a
/// push by its payload type, a local notification (`IosAppNotifier`) by the destination in its `userInfo`. Every ISU,
/// lesson and subject is synthetic.
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

    func testAFriendshipTapOpensTheActorsProfileOnTheMeTab() throws {
        let route = NotificationTaps.entryRoute(userInfo: userInfo(.friendship))

        XCTAssertEqual(route, EntryRoute(
            tab: .me, overlay: AppRoutes.UserProfile(isu: 100_002), request: nil, activity: nil, alert: nil,
            shortcutId: nil
        ))
        let router = readyRouter()
        XCTAssertTrue(router.open(entry: try XCTUnwrap(route)))
        XCTAssertEqual(router.selectedTab, .me)
        XCTAssertEqual(router.path(of: .me), [ShellDestination(AppRoutes.UserProfile(isu: 100_002))])
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

    // MARK: Local notification taps

    func testAScheduleChangeTapOpensTheChangesOverTheSchedule() throws {
        let router = try open(NotificationDestinationScheduleChanges.shared)

        XCTAssertEqual(router.selectedTab, .schedule)
        XCTAssertEqual(router.path(of: .schedule), [ShellDestination(AppRoutes.ScheduleChanges.shared)])
    }

    func testAMarksDigestTapOpensTheRecordbook() throws {
        let router = try open(NotificationDestinationRecordbook.shared)

        XCTAssertEqual(router.selectedTab, .recordbook)
        XCTAssertEqual(router.path(of: .recordbook), [])
        XCTAssertFalse(router.barsLoginRequested)
    }

    func testAMarksDigestOfOneSubjectOpensItsPage() throws {
        for args in [subject, subjectWithJournal] {
            let router = try open(NotificationDestinationRecordbookSubject(args: args))

            XCTAssertEqual(router.selectedTab, .recordbook)
            XCTAssertEqual(router.path(of: .recordbook), [ShellDestination(AppRoutes.RecordbookSubject(args: args))])
        }
    }

    func testTheBarsReminderTapAsksTheRecordbookForTheSignInOnce() throws {
        let router = try open(NotificationDestinationBarsLogin.shared)

        XCTAssertEqual(router.selectedTab, .recordbook)
        XCTAssertTrue(router.barsLoginRequested)
        XCTAssertTrue(router.consumeBarsLogin())
        XCTAssertFalse(router.consumeBarsLogin())
    }

    func testLocalSportAndProfileDestinationsOpenTheirTabs() throws {
        let sport = try open(NotificationDestinationSport.shared)
        XCTAssertEqual(sport.selectedTab, .sport)
        XCTAssertNil(sport.consumeRequest(of: .sport))

        let profile = try open(NotificationDestinationUserProfile(isu: 100_002))
        XCTAssertEqual(profile.selectedTab, .me)
        XCTAssertEqual(profile.path(of: .me), [ShellDestination(AppRoutes.UserProfile(isu: 100_002))])
    }

    /// What the system hands back: plain Foundation values, a number where an older build stored the ISU as one.
    func testAStoredUserInfoRoutesAsTheNotifierWroteIt() throws {
        let subjectInfo = NotificationTapRoutes.shared.userInfoOf(
            destination: NotificationDestinationRecordbookSubject(args: subjectWithJournal)
        )
        let archived = try NSKeyedArchiver.archivedData(withRootObject: subjectInfo, requiringSecureCoding: true)
        let stored = try XCTUnwrap(
            NSKeyedUnarchiver.unarchivedObject(
                ofClasses: [NSDictionary.self, NSString.self, NSNumber.self], from: archived
            ) as? [AnyHashable: Any]
        )
        XCTAssertEqual(
            NotificationTaps.entryRoute(userInfo: stored)?.overlay as? AppRoutes.RecordbookSubject,
            AppRoutes.RecordbookSubject(args: subjectWithJournal)
        )

        let numeric: [AnyHashable: Any] = [
            "action": AppEntryIntents.shared.ACTION_OPEN_USER_PROFILE, "isu": NSNumber(value: 100_002),
        ]
        XCTAssertEqual(
            NotificationTaps.entryRoute(userInfo: numeric)?.overlay as? AppRoutes.UserProfile,
            AppRoutes.UserProfile(isu: 100_002)
        )
    }

    func testAnActionNoLocalNotificationCarriesOpensNothing() {
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: ["action": AppEntryIntents.shared.ACTION_OPEN_QR_PASS]))
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: ["action": "android.intent.action.VIEW"]))
        XCTAssertNil(NotificationTaps.entryRoute(userInfo: [
            "action": AppEntryIntents.shared.ACTION_OPEN_USER_PROFILE, "isu": "0",
        ]))
    }

    /// The tap of a local notification of `destination`, with the `userInfo` `IosAppNotifier` writes, on a ready
    /// router through the delegate's queue.
    private func open(_ destination: NotificationDestination) throws -> AppRouter {
        let userInfo = NotificationTapRoutes.shared.userInfoOf(destination: destination)
        let route = try XCTUnwrap(NotificationTaps.entryRoute(userInfo: userInfo), "\(destination)")
        let router = readyRouter()
        let taps = NotificationTaps()
        taps.attach { route in router.open(entry: route) }
        taps.route(route)
        return router
    }

    private let subject = RecordbookSubjectArgs(
        entryId: 11, programId: 1, semester: 3, studyYear: "2026/2027", barsPlan: nil, barsType: nil,
        barsIdentifier: nil
    )
    private let subjectWithJournal = RecordbookSubjectArgs(
        entryId: 11, programId: 1, semester: 3, studyYear: "2026/2027", barsPlan: KotlinLong(value: 7),
        barsType: "flow", barsIdentifier: "7"
    )

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
