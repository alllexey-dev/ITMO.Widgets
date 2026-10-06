@testable import ITMOWidgets
import Shared
import WebKit
import XCTest

/// The pilot sign-in (IO-07a): the bundled interceptor's bridge and the `postTokens` message handler in a real
/// `WKWebView` hand the callback page's token response to the session, other pages and origins hand nothing, the
/// model maps failures as Android does, and the shell's gate follows the session states. A fake gateway stands in
/// for `SessionRepository`; pages are local HTML on an https base URL, so nothing reaches the network. Every token is
/// synthetic.
@MainActor
final class AuthTests: XCTestCase {
    private let tokenResponse = #"{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","id_token":"synthetic-id"}"#
    private var gateway: FakeSessionGateway!
    private var model: ItmoSignInModel!
    private var browser: ItmoSignInBrowser!

    override func setUp() async throws {
        try await super.setUp()
        gateway = FakeSessionGateway()
        model = ItmoSignInModel(gateway: gateway)
        browser = ItmoSignInBrowser(model: model)
    }

    override func tearDown() async throws {
        browser.webView.stopLoading()
        browser = nil
        model = nil
        gateway = nil
        try await super.tearDown()
    }

    // MARK: Message handler

    func testInterceptorIsBundledFromTheAndroidAsset() throws {
        let source = try XCTUnwrap(ItmoTokenBridge.interceptorSource())
        XCTAssertTrue(source.contains("window.ItmoAuthBridge.postTokens(responseText)"))
        XCTAssertTrue(source.contains("https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/token"))
    }

    func testCallbackPageTokensReachCompleteItmoIdLogin() async throws {
        try await load(page: "https://my.itmo.ru/login/callback?state=synthetic", script: postThroughBridge)

        await waitUntil { self.gateway.postedTokens.count == 1 }
        XCTAssertEqual(gateway.postedTokens, [tokenResponse])
        XCTAssertTrue(model.isCompleting, "a successful sign-in keeps the cover until the gate leaves the page")
        XCTAssertNil(model.errorKey)
    }

    func testBridgeExistsOnlyOnTheCallbackPage() async throws {
        try await load(page: "https://my.itmo.ru/login", script: "")
        let type = try await browser.webView.evaluateJavaScript("typeof window.ItmoAuthBridge") as? String
        XCTAssertEqual(type, "undefined")
    }

    func testTokensPostedOffTheCallbackPageAreIgnored() async throws {
        // Messages arrive in order, so the page's load message proves its token message was handled.
        try await load(page: "https://my.itmo.ru/login", script: postDirectly)

        XCTAssertFalse(model.isCompleting)
        XCTAssertEqual(gateway.postedTokens, [])
    }

    func testTokensFromAnotherOriginAreIgnored() async throws {
        try await load(page: "https://id.itmo.ru/login/callback", script: postDirectly)

        XCTAssertFalse(model.isCompleting)
        XCTAssertEqual(gateway.postedTokens, [])
    }

    func testOnlyHttpsPagesMayLoadInTheMainFrame() {
        XCTAssertTrue(ItmoAuthUrls.isNavigable(URL(string: "https://id.itmo.ru/auth")))
        XCTAssertTrue(ItmoAuthUrls.isNavigable(URL(string: "https://oauth.vk.com/authorize")))
        XCTAssertFalse(ItmoAuthUrls.isNavigable(URL(string: "http://my.itmo.ru/")))
        XCTAssertFalse(ItmoAuthUrls.isNavigable(URL(string: "vk://authorize")))
        XCTAssertFalse(ItmoAuthUrls.isNavigable(nil))

        XCTAssertTrue(ItmoAuthUrls.isTokenCallback(URL(string: "https://MY.ITMO.RU/login/callback?code=1")))
        XCTAssertFalse(ItmoAuthUrls.isTokenCallback(URL(string: "https://my.itmo.ru/login/callback/")))
        XCTAssertFalse(ItmoAuthUrls.isTokenCallback(URL(string: "https://my.itmo.ru.example.com/login/callback")))
        XCTAssertFalse(ItmoAuthUrls.isTokenCallback(URL(string: "http://my.itmo.ru/login/callback")))
    }

    // MARK: Model

    func testFailedSignInShowsAndroidsErrorAndAllowsAnother() async {
        let callback = URL(string: "https://my.itmo.ru/login/callback")
        gateway.result = AppResultFailure(error: AppErrorNetwork.shared)

        await model.tokensPosted(pageURL: callback, tokenResponseJson: tokenResponse)?.value

        XCTAssertFalse(model.isCompleting)
        XCTAssertEqual(model.errorKey, "auth_error_network")
        XCTAssertTrue(model.showsError)

        gateway.result = AppResultFailure(error: AppErrorUnauthorized.shared)
        await model.tokensPosted(pageURL: callback, tokenResponseJson: tokenResponse)?.value
        XCTAssertEqual(model.errorKey, "auth_error_invalid_credentials")

        gateway.result = AppResultFailure(error: AppErrorUnknown(cause: nil))
        await model.tokensPosted(pageURL: callback, tokenResponseJson: tokenResponse)?.value
        XCTAssertEqual(model.errorKey, "auth_error_unknown")
        XCTAssertEqual(gateway.postedTokens.count, 3)

        let generation = model.loadGeneration
        model.retry()
        XCTAssertFalse(model.showsError)
        XCTAssertEqual(model.page, .loading)
        XCTAssertEqual(model.loadGeneration, generation + 1)
    }

    func testOneHandOverAtATime() async {
        let callback = URL(string: "https://my.itmo.ru/login/callback")
        let first = model.tokensPosted(pageURL: callback, tokenResponseJson: tokenResponse)

        XCTAssertNotNil(first)
        XCTAssertNil(model.tokensPosted(pageURL: callback, tokenResponseJson: tokenResponse))
        await first?.value
        XCTAssertEqual(gateway.postedTokens.count, 1)
    }

    func testFailedPageStaysFailedUntilRetry() {
        model.mainFrameFailed()
        model.pageStarted()
        model.pageFinished()
        XCTAssertEqual(model.page, .failed)
        XCTAssertEqual(model.errorTitleKey, "auth_web_error")

        model.retry()
        model.pageFinished()
        XCTAssertEqual(model.page, .shown)
        XCTAssertFalse(model.showsError)
    }

    // MARK: Gate

    func testGateFollowsTheSessionStates() async {
        let session = ShellSession(gateway: gateway)
        XCTAssertEqual(session.state, .loading)
        let follow = Task { await session.follow() }
        defer { follow.cancel() }
        await waitUntil { self.gateway.isObserved }

        let steps: [(SessionState, ShellSessionState)] = [
            (SessionStateInitializing.shared, .loading),
            (SessionStateSignedOut.shared, .signedOut),
            (SessionStateSignedIn(user: nil, demo: false), .signedIn),
            (SessionStateSigningOut.shared, .loading),
            (SessionStateReauthenticationRequired.shared, .signedOut),
            (SessionStateSignedIn(user: nil, demo: true), .demo),
        ]
        for (sessionState, expected) in steps {
            gateway.emit(sessionState)
            await waitUntil { session.state == expected }
        }
    }

    func testSignOutGoesThroughTheRepositoryOnce() async {
        let session = ShellSession(gateway: gateway)

        await session.signOut()?.value

        XCTAssertEqual(gateway.signOutCalls, 1)
        XCTAssertEqual(session.state, .loading, "the gate waits for the repository's states")
        session.signIn()
        XCTAssertEqual(session.state, .loading, "the shared session signs in only on the ITMO.ID page")
    }

    func testFixtureSessionChangesWithoutARepository() async {
        let session = ShellSession(state: .demo)
        await session.follow()
        XCTAssertNil(session.signOut())
        XCTAssertEqual(session.state, .signedOut)
        session.signIn()
        XCTAssertEqual(session.state, .signedIn)
    }

    // MARK: Helpers

    private var postThroughBridge: String {
        "window.ItmoAuthBridge.postTokens('\(tokenResponse)');"
    }

    private var postDirectly: String {
        "window.webkit.messageHandlers.\(ItmoTokenBridge.handlerName).postMessage('\(tokenResponse)');"
    }

    /// Loads local HTML as `page` (its origin and URL) that runs `script`, then reports the load through a test
    /// handler, and waits for that report.
    private func load(page: String, script: String) async throws {
        let reporter = PageLoadReporter()
        let controller = browser.webView.configuration.userContentController
        controller.add(reporter, name: PageLoadReporter.name)
        defer { controller.removeScriptMessageHandler(forName: PageLoadReporter.name) }

        let html = """
        <html><body>
        <script>\(script)</script>
        <script>window.webkit.messageHandlers.\(PageLoadReporter.name).postMessage(location.href);</script>
        </body></html>
        """
        browser.webView.loadHTMLString(html, baseURL: URL(string: page))
        await waitUntil { reporter.loaded.contains(page) }
        XCTAssertEqual(browser.webView.url?.absoluteString, page)
    }

    private func waitUntil(timeout: TimeInterval = 5, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(condition(), "the condition did not hold within \(timeout) s")
    }
}

/// `SessionRepository` as the shell and the sign-in page see it, driven by the test.
@MainActor
private final class FakeSessionGateway: SessionGateway {
    var result: AppResult = AppResultSuccess<AnyObject>(value: nil)
    private(set) var postedTokens: [String] = []
    private(set) var signOutCalls = 0
    private var continuation: AsyncStream<SessionState>.Continuation?

    var isObserved: Bool { continuation != nil }

    func states() -> AsyncStream<SessionState> {
        let (stream, continuation) = AsyncStream<SessionState>.makeStream()
        self.continuation = continuation
        return stream
    }

    func emit(_ state: SessionState) {
        continuation?.yield(state)
    }

    func completeItmoIdLogin(tokenResponseJson: String) async -> AppResult {
        postedTokens.append(tokenResponseJson)
        return result
    }

    func signOut() async {
        signOutCalls += 1
    }
}

/// Collects the pages that reported their load.
@MainActor
private final class PageLoadReporter: NSObject, WKScriptMessageHandler {
    static let name = "itmoTestPageLoaded"
    private(set) var loaded: [String] = []

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        if let href = message.body as? String { loaded.append(href) }
    }
}
