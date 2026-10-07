@testable import ITMOWidgets
import Security
import Shared
import WebKit
import XCTest

/// The BARS session on iOS (L18 IO-09d1) inside the app's entitlements and its started graph: WebKit's ITMO.ID
/// cookies copied to the Keychain and replayed as the `Cookie` header of the authorization URL, a callback with
/// another `state` rejected, one `BarsClient` in the graph, and sign-out emptying the BARS item and the cookie copy.
/// Every value is synthetic; no test reaches the network.
@MainActor
final class BarsSessionTests: XCTestCase {
    private let service = "dev.alllexey.itmowidgets"
    private let barsItem = "bars_tokens.enc"
    private let cookieItem = KeychainItmoIdCookies.companion.ITEM
    private let cookieNames = ["KEYCLOAK_IDENTITY", "KEYCLOAK_SESSION", "_ym_uid"]

    override func tearDown() async throws {
        IosSecureStore.shared.keychain().delete(name: cookieItem)
        IosSecureStore.shared.keychain().delete(name: barsItem)
        await removeSyntheticCookies()
        try await super.tearDown()
    }

    func testTheWebKitCookiesReachTheKeychainAndTheReplayHeader() async throws {
        let store = WKWebsiteDataStore.default().httpCookieStore
        await store.setCookie(try cookie("KEYCLOAK_IDENTITY", "synthetic-identity", domain: "id.itmo.ru",
                                         path: "/auth/realms/itmo/"))
        await store.setCookie(try cookie("KEYCLOAK_SESSION", "synthetic-session", domain: ".id.itmo.ru",
                                         path: "/auth/realms/itmo/"))
        await store.setCookie(try cookie("_ym_uid", "synthetic-metrica", domain: ".itmo.ru", path: "/"))

        try await graph(ItmoIdCookieExport.self).run()

        let item = try XCTUnwrap(itemValue(account: cookieItem), "no Keychain item \(cookieItem)")
        XCTAssertTrue(item.contains("KEYCLOAK_IDENTITY"))
        XCTAssertTrue(item.contains("KEYCLOAK_SESSION"))
        XCTAssertFalse(item.contains("_ym_uid"), "only id.itmo.ru cookies are copied")

        let url = repository().loginUrl(state: "synthetic-state")
        XCTAssertTrue(url.hasPrefix("https://id.itmo.ru/"))
        let header = try await graph(KeychainItmoIdCookies.self).cookieHeader(url: url)
        XCTAssertEqual(
            Set(try XCTUnwrap(header).components(separatedBy: "; ")),
            ["KEYCLOAK_IDENTITY=synthetic-identity", "KEYCLOAK_SESSION=synthetic-session"]
        )
        let elsewhere = try await graph(KeychainItmoIdCookies.self).cookieHeader(url: "https://bars.itmo.ru/rest/login")
        XCTAssertNil(elsewhere, "the copy goes to ITMO.ID only")
    }

    func testACallbackWithAnotherStateIsRejected() async throws {
        let callback = "https://bars.itmo.ru/rest/login?state=another-state&code=synthetic-code"

        let result = try await repository().completeLogin(callbackUrl: callback, expectedState: "synthetic-state")
        let failure = try XCTUnwrap(result as? AppResultFailure)
        XCTAssertTrue(failure.error is AppErrorUnauthorized)

        // The sheet's ViewModel, as BarsLoginSheet resolves it: a fresh state per sheet, the callback accepted only
        // with it.
        let model = ObservableViewModel<BarsLoginViewModel, BarsLoginUiState>(state: \.uiState) { store in
            store.resolve(type: BarsLoginViewModel.self, parameters: BarsLoginParameters.shared.fresh())
                as! BarsLoginViewModel
        }
        let other = ObservableViewModel<BarsLoginViewModel, BarsLoginUiState>(state: \.uiState) { store in
            store.resolve(type: BarsLoginViewModel.self, parameters: BarsLoginParameters.shared.fresh())
                as! BarsLoginViewModel
        }
        XCTAssertNotEqual(model.viewModel.loginUrl, other.viewModel.loginUrl)
        XCTAssertTrue(model.viewModel.isCallback(url: callback))
        model.viewModel.complete(url: callback)
        await waitUntil { model.viewModel.uiState.value.error != nil }
        XCTAssertTrue(model.viewModel.uiState.value.error is AppErrorUnauthorized)
        XCTAssertFalse(model.viewModel.uiState.value.completing)
    }

    func testTheGraphHoldsOneBarsClientAndTheIosPorts() throws {
        XCTAssertTrue(try graph(BarsClient.self) === graph(BarsClient.self))
        XCTAssertTrue(try graph(OwnerBoundBarsStorage.self) === graph(OwnerBoundBarsStorage.self))
        XCTAssertTrue(try graph(BarsTokenStore.self) === graph(BarsTokenStore.self))
        XCTAssertTrue(IosKoin.shared.get(protocol: BarsSilentLogin.self) is WebViewBarsSilentLogin)
        let cookies = try graph(KeychainItmoIdCookies.self)
        XCTAssertTrue(IosKoin.shared.get(protocol: ItmoIdCookies.self) as AnyObject === cookies)
    }

    func testSignOutEmptiesTheBarsItemAndTheCookieCopy() async throws {
        try await graph(BarsTokenStore.self).install(owner: 123456, header: "Bearer synthetic-bars-header")
        try await graph(KeychainItmoIdCookies.self).replaceFromWebKit(cookies: [
            WebKitCookie(name: "KEYCLOAK_IDENTITY", value: "synthetic-identity", domain: "id.itmo.ru", path: "/",
                         secure: true, expiresAtEpochMillis: nil),
        ])
        XCTAssertNotNil(itemValue(account: barsItem))
        XCTAssertNotNil(itemValue(account: cookieItem))

        // The app graph's BARS cleaner, then the core's (the Keychain, the App Group, WebKit), as sign-out runs them.
        try await graph(BarsPreferenceRepositoryImpl.self).clearSessionData()
        XCTAssertNil(itemValue(account: barsItem))
        let core = IosCoreGraph(host: AppPlatform())
        defer { core.close() }
        for cleaner in core.sessionDataCleaners {
            try await cleaner.clearSessionData()
        }

        XCTAssertNil(itemValue(account: barsItem))
        XCTAssertNil(itemValue(account: cookieItem))
    }

    // MARK: Helpers

    private func graph<T: AnyObject>(_ type: T.Type) throws -> T {
        try XCTUnwrap(IosKoin.shared.get(type: type) as? T, "Koin resolved no \(T.self)")
    }

    private func repository() -> BarsSessionRepository {
        IosKoin.shared.get(protocol: BarsSessionRepository.self) as! BarsSessionRepository
    }

    private func cookie(_ name: String, _ value: String, domain: String, path: String) throws -> HTTPCookie {
        try XCTUnwrap(HTTPCookie(properties: [
            .domain: domain,
            .path: path,
            .name: name,
            .value: value,
            .secure: "TRUE",
            .expires: Date().addingTimeInterval(86_400),
        ]))
    }

    private func removeSyntheticCookies() async {
        let store = WKWebsiteDataStore.default().httpCookieStore
        for cookie in await store.allCookies() where cookieNames.contains(cookie.name) {
            await store.deleteCookie(cookie)
        }
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

    private func waitUntil(timeout: TimeInterval = 5, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(condition(), "the condition did not hold within \(timeout) s")
    }
}
