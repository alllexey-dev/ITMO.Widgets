@testable import ITMOWidgets
import XCTest

/// Pins every iOS identifier that is frozen from the first TestFlight build (lane L18 Invariants, ADR 0023), read
/// from the built bundles. Each IO card that adds an identifier appends its assertion here.
final class StableIdentifiersTests: XCTestCase {
    private let appGroup = "group.dev.alllexey.itmowidgets"
    private let keychainGroupSuffix = "dev.alllexey.itmowidgets.shared"

    func testAppBundle() throws {
        let info = try XCTUnwrap(Bundle.main.infoDictionary)
        XCTAssertEqual(Bundle.main.bundleIdentifier, "dev.alllexey.itmowidgets")
        XCTAssertEqual(info["AppGroupID"] as? String, appGroup)
        assertKeychainGroup(info["KeychainGroup"] as? String)
        XCTAssertEqual(info["MinimumOSVersion"] as? String, "18.0")
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
        XCTAssertEqual(appVersion, "2.3.0")
        for name in ["ITMOWidgetsWidgets", "ITMOWidgetsNotificationService"] {
            XCTAssertEqual(try extensionInfo(name)["CFBundleShortVersionString"] as? String, appVersion, name)
        }
    }

    /// Widgets, Controls and shortcuts hold `itmowidgets://route/<id>` URLs (IO-06b).
    func testRouteUrls() throws {
        let urlTypes = try XCTUnwrap(Bundle.main.infoDictionary?["CFBundleURLTypes"] as? [[String: Any]])
        let schemes = urlTypes.flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }
        XCTAssertEqual(schemes, ["itmowidgets"])
        XCTAssertEqual(RouteURL.scheme, "itmowidgets")
        XCTAssertEqual(
            RouteURL.ids.compactMap { RouteURL.url(id: $0)?.absoluteString },
            ["schedule", "home", "sport", "me", "qr_pass", "today"].map { "itmowidgets://route/\($0)" }
        )
    }

    /// `$(AppIdentifierPrefix)` is the team ID plus a dot on a device, `FAKETEAMID.` on the simulator and empty for
    /// a team-less device build (SP-23), so only the suffix is stable.
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
