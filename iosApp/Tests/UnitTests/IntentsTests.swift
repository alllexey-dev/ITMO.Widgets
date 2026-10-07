@testable import ITMOWidgets
import AppIntents
import Shared
import UIKit
import XCTest

/// The system entries of IO-11 into the router: the QR Control's `OpenRouteIntent`, the App Shortcuts' intents and
/// the Home Screen quick actions all offer a route id through `RouteInbox` to `AppRouter.open(id:)`. XCUITest cannot
/// drive Siri, Control Center or the springboard reliably, so each `perform()` and the quick-action handler run here
/// against a router of the test's own.
@MainActor
final class IntentsTests: XCTestCase {
    private var router: AppRouter!

    override func setUp() async throws {
        let router = AppRouter()
        router.sessionChanged(ShellSessionState.signedIn.sessionState, onboarding: .passed)
        router.shellMounted(true)
        router.selectedTab = .sport
        RouteInbox.shared.connect { router.open(id: $0) }
        self.router = router
    }

    // MARK: Control

    func testQrControlOpensPassOverHome() async throws {
        _ = try await QrControl.action.perform()

        assertQrPassOpened()
    }

    func testOpenRouteIntentOpensItsTarget() async throws {
        _ = try await OpenRouteIntent(route: .today).perform()

        assertTodayOpened()
    }

    func testControlIntentOpensTheApp() {
        XCTAssertEqual(QrControl.action.target, .qrPass)
        XCTAssertTrue(OpenRouteIntent.openAppWhenRun, "an OpenIntent brings the app forward")
        XCTAssertFalse(OpenRouteIntent.isDiscoverable, "Shortcuts lists the App Shortcuts instead")
    }

    // MARK: App Shortcuts

    func testQrPassShortcutOpensPassOverHome() async throws {
        _ = try await OpenQrPassIntent().perform()

        assertQrPassOpened()
    }

    func testTodayShortcutOpensScheduleOnToday() async throws {
        _ = try await OpenTodayIntent().perform()

        assertTodayOpened()
    }

    func testShortcutIntentsOpenTheApp() {
        XCTAssertTrue(OpenQrPassIntent.openAppWhenRun)
        XCTAssertTrue(OpenTodayIntent.openAppWhenRun)
        XCTAssertEqual(ITMOWidgetsShortcuts.appShortcuts.count, 2)
    }

    func testShortcutPhrasesAreRussianWithTheAppName() {
        let phrases = [
            "Open the QR pass in ${applicationName}": "Открой QR-пропуск в ${applicationName}",
            "Show the pass of ${applicationName}": "Покажи пропуск ${applicationName}",
            "Today in ${applicationName}": "Расписание на сегодня в ${applicationName}",
            "Lessons today in ${applicationName}": "Какие пары сегодня в ${applicationName}",
        ]
        for (key, expected) in phrases {
            XCTAssertEqual(Bundle.main.localizedString(forKey: key, value: nil, table: "AppShortcuts"), expected, key)
        }
    }

    // MARK: Quick actions

    func testQuickActionsRoute() {
        XCTAssertTrue(QuickActions.perform(item("dev.alllexey.itmowidgets.qr_pass")))
        assertQrPassOpened()

        XCTAssertTrue(QuickActions.perform(item("dev.alllexey.itmowidgets.today")))
        assertTodayOpened()
    }

    func testUnknownQuickActionOpensNothing() {
        for type in ["dev.alllexey.itmowidgets.nowhere", "dev.alllexey.itmowidgets", "other.app.qr_pass", "qr_pass"] {
            XCTAssertFalse(QuickActions.perform(item(type)), type)
        }
        XCTAssertEqual(router.selectedTab, .sport)
        XCTAssertNil(router.pendingRoute)
        XCTAssertNil(router.sheet)
    }

    func testQuickActionTitlesResolveFromInfoPlist() throws {
        let items = try XCTUnwrap(Bundle.main.infoDictionary?["UIApplicationShortcutItems"] as? [[String: Any]])
        let titles = items.compactMap { $0["UIApplicationShortcutItemTitle"] as? String }
        XCTAssertEqual(titles, ["shortcut_qr_long", "shortcut_today_long"])
        XCTAssertEqual(
            titles.map { Bundle.main.localizedString(forKey: $0, value: nil, table: "InfoPlist") },
            ["Открыть QR-пропуск", "Расписание на сегодня"]
        )
        XCTAssertEqual(
            items.compactMap { $0["UIApplicationShortcutItemIconSymbolName"] as? String },
            [AppSymbol.qrCode.systemName, "calendar"]
        )
    }

    // MARK: Inbox

    func testRouteWaitsForTheRouter() {
        let inbox = RouteInbox()
        var opened: [String] = []

        inbox.offer(id: "qr_pass")
        inbox.offer(id: "today")
        XCTAssertEqual(inbox.waiting, "today", "a newer id replaces the waiting one")

        inbox.connect { opened.append($0); return true }

        XCTAssertEqual(opened, ["today"])
        XCTAssertNil(inbox.waiting)
        XCTAssertTrue(inbox.offer(id: "qr_pass"))
        XCTAssertEqual(opened, ["today", "qr_pass"])
    }

    func testEveryIntentRouteIsARouteId() {
        for route in IntentRoute.allCases {
            XCTAssertTrue(RouteURL.ids.contains(route.rawValue), route.rawValue)
            XCTAssertNotNil(RouteURL.entryRoute(id: route.rawValue), route.rawValue)
        }
    }

    // MARK: Helpers

    private func item(_ type: String) -> UIApplicationShortcutItem {
        UIApplicationShortcutItem(type: type, localizedTitle: type)
    }

    private func assertQrPassOpened(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(router.selectedTab, .home, file: file, line: line)
        XCTAssertEqual(router.path(of: .home), [ShellDestination(AppRoutes.QrPass.shared)], file: file, line: line)
        XCTAssertNil(router.pendingRoute, file: file, line: line)
    }

    private func assertTodayOpened(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(router.selectedTab, .schedule, file: file, line: line)
        XCTAssertEqual(router.path(of: .schedule), [], file: file, line: line)
        XCTAssertGreaterThan(router.todayRequest, 0, file: file, line: line)
    }
}
