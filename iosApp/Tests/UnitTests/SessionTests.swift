import Security
import Shared
import XCTest

/// The iOS session through the real core graph inside the app's entitlements, which a Kotlin/Native
/// test binary lacks: one Keychain item read by MyItmoApi and by the session, and the sign-out cleaners emptying
/// the Keychain and the App Group container and asking the host to clear WebKit. Every value is synthetic.
@MainActor
final class SessionTests: XCTestCase {
    private let service = "dev.alllexey.itmowidgets"
    private let otherItem = "session-tests-other"
    private var host: FakeCoreHost!
    private var graph: IosCoreGraph!

    override func setUp() async throws {
        try await super.setUp()
        host = FakeCoreHost()
        graph = IosCoreGraph(host: host)
        graph.tokenStorage.clearTokens()
    }

    override func tearDown() async throws {
        graph.tokenStorage.clearTokens()
        IosSecureStore.shared.keychain().delete(name: otherItem)
        graph.close()
        try await super.tearDown()
    }

    func testTokensReachBothInterfacesThroughOneKeychainItem() async throws {
        let storage = graph.tokenStorage
        // The five fields Android seals into myitmo_tokens.enc.
        let value = [
            base64URL("synthetic-access"), "1790000300000",
            base64URL("synthetic-refresh"), "1790003600000",
            base64URL("synthetic-id")
        ].joined(separator: "\n")
        IosSecureStore.shared.keychain().write(name: KeychainTokenStorage.companion.ITEM, value: value)

        let tokens = try await storage.read()
        XCTAssertEqual(tokens?.accessToken, "synthetic-access")
        XCTAssertEqual(tokens?.refreshToken, "synthetic-refresh")
        XCTAssertEqual(tokens?.idToken, "synthetic-id")

        try await storage.write(tokens: nil)
        XCTAssertFalse(storage.hasRefreshToken())
        XCTAssertNil(itemValue(account: KeychainTokenStorage.companion.ITEM))

        try await storage.write(tokens: tokens)
        XCTAssertTrue(storage.hasRefreshToken())
        XCTAssertEqual(storage.getIdToken(), "synthetic-id")
        XCTAssertEqual(itemValue(account: KeychainTokenStorage.companion.ITEM), value)
    }

    func testSignOutCleanersEmptyTheKeychainTheAppGroupAndClearWebKit() async throws {
        graph.tokenStorage.replaceWithRefreshToken(refreshToken: "synthetic-refresh")
        IosSecureStore.shared.keychain().write(name: otherItem, value: "synthetic")
        let snapshot = URL(fileURLWithPath: graph.appGroupDirectory.file(name: "session-tests-v1.json").description())
        try Data("{}".utf8).write(to: snapshot)

        let cleaners = graph.sessionDataCleaners
        XCTAssertEqual(cleaners.count, 3)
        for cleaner in cleaners {
            try await cleaner.clearSessionData()
        }

        XCTAssertFalse(hasAnyItemOfTheService(), "the Keychain still holds an item of \(service)")
        XCTAssertFalse(FileManager.default.fileExists(atPath: snapshot.path))
        XCTAssertEqual(host.websiteDataClears, 1)
    }

    private func itemValue(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func hasAnyItemOfTheService() -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecReturnAttributes as String: true,
            kSecMatchLimit as String: kSecMatchLimitAll,
        ]
        var result: CFTypeRef?
        return SecItemCopyMatching(query as CFDictionary, &result) != errSecItemNotFound
    }

    private func base64URL(_ text: String) -> String {
        Data(text.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

/// The part of `IosPlatform` the core graph calls, with WebKit's removal only counted: the real
/// `WKWebsiteDataStore.removeData` also deletes Screen Time web history, which hangs for minutes on a CI simulator
/// without that service and stalls every WebKit store call after it.
final class FakeCoreHost: NSObject, IosCoreHost {
    private(set) var websiteDataClears = 0

    func topViewController() -> UIViewController? { nil }

    func reload(kind: String) {}

    func clearWebsiteData(completion: @escaping () -> Void) {
        websiteDataClears += 1
        completion()
    }
}
