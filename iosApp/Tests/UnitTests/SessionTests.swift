import Security
import Shared
import WebKit
import XCTest

/// The iOS session (L18 IO-04b) through the real core graph inside the app's entitlements, which a Kotlin/Native
/// test binary lacks: one Keychain item read by MyItmoApi and by the session, and the sign-out cleaners emptying
/// the Keychain, the App Group container and WebKit. Every value is synthetic.
@MainActor
final class SessionTests: XCTestCase {
    private let service = "dev.alllexey.itmowidgets"
    private let otherItem = "session-tests-other"
    private let cookieName = "session-tests"
    private var host: WebKitHost!
    private var graph: IosCoreGraph!

    override func setUp() async throws {
        try await super.setUp()
        host = WebKitHost()
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
        storage.replaceWithTokens(
            tokens: SessionTokens(
                accessToken: "synthetic-access",
                accessExpiresInSeconds: 300,
                refreshToken: "synthetic-refresh",
                refreshExpiresInSeconds: 3600,
                idToken: "synthetic-id"
            )
        )

        let tokens = try await storage.read()
        XCTAssertEqual(tokens?.accessToken, "synthetic-access")
        XCTAssertEqual(tokens?.refreshToken, "synthetic-refresh")
        XCTAssertEqual(tokens?.idToken, "synthetic-id")

        // The five fields Android seals into myitmo_tokens.enc.
        let value = try XCTUnwrap(itemValue(account: KeychainTokenStorage.companion.ITEM))
        let fields = value.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        XCTAssertEqual(fields.count, 5)
        XCTAssertEqual(fields[0], base64URL("synthetic-access"))
        XCTAssertNotNil(Int64(fields[1]))
        XCTAssertEqual(fields[4], base64URL("synthetic-id"))

        try await storage.write(tokens: nil)
        XCTAssertFalse(storage.hasRefreshToken())
        XCTAssertNil(itemValue(account: KeychainTokenStorage.companion.ITEM))

        try await storage.write(tokens: tokens)
        XCTAssertTrue(storage.hasRefreshToken())
        XCTAssertEqual(storage.getIdToken(), "synthetic-id")
    }

    func testSignOutCleanersEmptyTheKeychainTheAppGroupAndWebKit() async throws {
        graph.tokenStorage.replaceWithRefreshToken(refreshToken: "synthetic-refresh")
        IosSecureStore.shared.keychain().write(name: otherItem, value: "synthetic")
        let snapshot = URL(fileURLWithPath: graph.appGroupDirectory.file(name: "session-tests-v1.json").description())
        try Data("{}".utf8).write(to: snapshot)
        let cookies = WKWebsiteDataStore.default().httpCookieStore
        await cookies.setCookie(try XCTUnwrap(syntheticCookie()))
        let cookiesBefore = await cookies.allCookies().filter { $0.name == cookieName }
        XCTAssertEqual(cookiesBefore.count, 1)

        let cleaners = graph.sessionDataCleaners
        XCTAssertEqual(cleaners.count, 3)
        for cleaner in cleaners {
            try await cleaner.clearSessionData()
        }

        XCTAssertFalse(hasAnyItemOfTheService(), "the Keychain still holds an item of \(service)")
        XCTAssertFalse(FileManager.default.fileExists(atPath: snapshot.path))
        XCTAssertEqual(host.websiteDataClears, 1)
        let cookiesAfter = await cookies.allCookies().filter { $0.name == cookieName }
        XCTAssertEqual(cookiesAfter, [])
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

    private func syntheticCookie() -> HTTPCookie? {
        HTTPCookie(properties: [
            .domain: "id.itmo.ru",
            .path: "/",
            .name: cookieName,
            .value: "synthetic",
            .secure: "TRUE",
        ])
    }
}

/// The part of IO-05's `IosPlatform` the core graph calls; WebKit's removal here is the real one.
private final class WebKitHost: NSObject, IosCoreHost {
    private(set) var websiteDataClears = 0

    func topViewController() -> UIViewController? { nil }

    func reload(kind: String) {}

    func clearWebsiteData(completion: @escaping () -> Void) {
        websiteDataClears += 1
        WKWebsiteDataStore.default().removeData(
            ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
            modifiedSince: .distantPast,
            completionHandler: completion
        )
    }
}
