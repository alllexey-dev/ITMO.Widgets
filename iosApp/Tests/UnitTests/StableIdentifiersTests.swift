@testable import ITMOWidgets
import XCTest

/// Pins every iOS identifier that is frozen from the first TestFlight build, read
/// from the built bundles. A new identifier gets its assertion here.
final class StableIdentifiersTests: XCTestCase {
    private let appGroup = "group.dev.alllexey.itmowidgets"
    private let keychainGroupSuffix = "dev.alllexey.itmowidgets.shared"

    func testAppBundle() throws {
        let info = try XCTUnwrap(Bundle.main.infoDictionary)
        XCTAssertEqual(Bundle.main.bundleIdentifier, "dev.alllexey.itmowidgets")
        XCTAssertEqual(info["AppGroupID"] as? String, appGroup)
        assertKeychainGroup(info["KeychainGroup"] as? String)
        XCTAssertEqual(info["MinimumOSVersion"] as? String, "26.1")
    }

    func testWidgetExtensionBundle() throws {
        let info = try extensionInfo("ITMOWidgetsWidgets")
        XCTAssertEqual(info["CFBundleIdentifier"] as? String, "dev.alllexey.itmowidgets.widgets")
        XCTAssertEqual(info["AppGroupID"] as? String, appGroup)
        XCTAssertNil(info["KeychainGroup"], "the widget reads App Group JSON only, no Keychain")
        XCTAssertEqual(extensionPoint(info), "com.apple.widgetkit-extension")
    }

    func testNotificationServiceBundle() throws {
        let info = try extensionInfo("ITMOWidgetsNotificationService")
        XCTAssertEqual(info["CFBundleIdentifier"] as? String, "dev.alllexey.itmowidgets.notification-service")
        XCTAssertEqual(info["AppGroupID"] as? String, appGroup)
        assertKeychainGroup(info["KeychainGroup"] as? String)
        XCTAssertEqual(extensionPoint(info), "com.apple.usernotifications.service")
    }

    func testExtensionsShareTheAppVersion() throws {
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        // A sideloaded beta carries its suffix (`2.3.0-beta.3`); App Store builds stay `2.3.0`.
        XCTAssertTrue(appVersion?.hasPrefix("2.3.0") == true, "\(appVersion ?? "nil")")
        for name in ["ITMOWidgetsWidgets", "ITMOWidgetsNotificationService"] {
            XCTAssertEqual(try extensionInfo(name)["CFBundleShortVersionString"] as? String, appVersion, name)
        }
    }

    /// Widgets, Controls and shortcuts hold `itmowidgets://route/<id>` URLs.
    func testRouteUrls() throws {
        let urlTypes = try XCTUnwrap(Bundle.main.infoDictionary?["CFBundleURLTypes"] as? [[String: Any]])
        let schemes = urlTypes.flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }
        XCTAssertEqual(schemes, ["itmowidgets"])
        XCTAssertEqual(RouteURL.scheme, "itmowidgets")
        XCTAssertEqual(
            RouteURL.ids.compactMap { RouteURL.url(id: $0)?.absoluteString },
            ["recordbook", "schedule", "home", "sport", "me", "qr_pass", "today", "lesson"].map { "itmowidgets://route/\($0)" }
        )
    }

    /// Placed widgets keep their kind and URL; the extensions find their App Group files by name.
    func testQrWidget() {
        XCTAssertEqual(QrWidget.kind, "dev.alllexey.itmowidgets.widget.qr")
        XCTAssertEqual(QrWidget.passURL, RouteURL.url(id: "qr_pass"))
        XCTAssertEqual(WidgetRoute.pass, QrWidget.passURL)
        XCTAssertEqual(
            [SessionFile.fileName, QrPassSnapshot.fileName, QrWidgetReveal.fileName],
            ["session-v1.json", "qr-pass-v1.json", "qr-widget-v1.json"]
        )
    }

    /// The notification service and the app meet in this App Group directory (`AppGroupSportResultInbox.DIRECTORY`).
    func testSportResultInbox() {
        XCTAssertEqual(SportResultInbox.name, "sport-results")
    }

    /// Placed Controls keep their kind; quick actions are `<bundle ID>.<route id>`, Android's shortcut ids.
    func testControlAndQuickActions() throws {
        XCTAssertEqual(QrControl.kind, "dev.alllexey.itmowidgets.control.qr")
        XCTAssertEqual(IntentRoute.allCases.map(\.rawValue), ["qr_pass", "today"])
        let items = try XCTUnwrap(Bundle.main.infoDictionary?["UIApplicationShortcutItems"] as? [[String: Any]])
        XCTAssertEqual(
            items.compactMap { $0["UIApplicationShortcutItemType"] as? String },
            ["dev.alllexey.itmowidgets.qr_pass", "dev.alllexey.itmowidgets.today"]
        )
    }

    /// The next-lesson widget, the classic list (the first builds' day widget), the day widget and the cards keep their kinds, the
    /// app's writer reloads them by name and writes the timeline file they read; the day's header opens the schedule
    /// on today, a lesson its sheet.
    func testScheduleWidgets() {
        XCTAssertEqual(NextLessonWidget.kind, "dev.alllexey.itmowidgets.widget.single-lesson")
        XCTAssertEqual(ClassicListWidget.kind, "dev.alllexey.itmowidgets.widget.day-schedule")
        XCTAssertEqual(DayWidget.kind, "dev.alllexey.itmowidgets.widget.day-timeline")
        XCTAssertEqual(CardsWidget.kind, "dev.alllexey.itmowidgets.widget.cards")
        XCTAssertEqual(CardsWidgetPage.fileName, "cards-widget-v1.json")
        XCTAssertEqual(WidgetRoute.today, RouteURL.url(id: "today"))
        XCTAssertEqual(ScheduleTimeline.fileName, "schedule-timeline-v2.json")
        XCTAssertEqual(
            [WidgetRoute.lessonArguments, WidgetRoute.sportLesson, WidgetRoute.sportPredicted], ["args", "lesson", "predicted"]
        )
    }

    /// The sport widget keeps its kind and file; a tap opens the sport tab.
    func testSportWidget() {
        XCTAssertEqual(SportWidget.kind, "dev.alllexey.itmowidgets.widget.sport")
        XCTAssertEqual(SportTimeline.fileName, "sport-widget-v1.json")
        XCTAssertEqual(WidgetRoute.sport, RouteURL.url(id: "sport"))
    }

    /// The app refresh task keeps its identifier: the system drops a scheduled request whose identifier the app no
    /// longer permits.
    func testBackgroundRefreshTask() {
        XCTAssertEqual(BackgroundRefresh.taskIdentifier, "dev.alllexey.itmowidgets.refresh")
        XCTAssertEqual(
            Bundle.main.infoDictionary?["BGTaskSchedulerPermittedIdentifiers"] as? [String],
            ["dev.alllexey.itmowidgets.refresh"]
        )
    }

    /// `$(AppIdentifierPrefix)` is the team ID plus a dot on a device, `FAKETEAMID.` on the simulator and empty for
    /// a team-less device build, so only the suffix is stable.
    private func assertKeychainGroup(_ group: String?, file: StaticString = #filePath, line: UInt = #line) {
        let group = group ?? ""
        XCTAssertTrue(group.hasSuffix(keychainGroupSuffix), "KeychainGroup = \(group)", file: file, line: line)
        XCTAssertFalse(group.contains("$("), "unexpanded build setting in \(group)", file: file, line: line)
    }

    private func extensionInfo(_ name: String) throws -> [String: Any] {
        let plugIns = try XCTUnwrap(Bundle.main.builtInPlugInsURL)
        let bundle = try XCTUnwrap(Bundle(url: plugIns.appendingPathComponent("\(name).appex")), name)
        return try XCTUnwrap(bundle.infoDictionary, name)
    }

    private func extensionPoint(_ info: [String: Any]) -> String? {
        (info["NSExtension"] as? [String: Any])?["NSExtensionPointIdentifier"] as? String
    }
}
