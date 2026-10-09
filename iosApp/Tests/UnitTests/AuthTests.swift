@testable import ITMOWidgets
import Shared
import WebKit
import XCTest

/// The sign-in (IO-07a, on `InteractiveLoginViewModel` since IO-07b): the bundled interceptor's bridge and the
/// `postTokens` message handler in a real `WKWebView` hand the callback page's token response to the shared
/// ViewModel and through it to `SessionRepository`, other pages and origins hand nothing, and the shell's gate follows
/// the session states and the first-run flag. The page model runs on the app's graph; a token response it cannot
/// parse fails before anything is stored, so the hand-over shows as `auth_error_invalid_credentials`. A fake gateway
/// stands in for the session of the gate. Pages are local HTML on an https base URL, so nothing reaches the network.
/// Every token is synthetic.
@MainActor
final class AuthTests: XCTestCase {
    /// Not an ITMO.ID token response: `completeItmoIdLogin` rejects it without storing anything.
    private let tokenResponse = #"{"synthetic":"not-a-token-response"}"#
    private var gateway: FakeSessionGateway!
    private var model: ItmoSignInModel!
    private var browser: ItmoSignInBrowser!

    override func setUp() async throws {
        try await super.setUp()
        gateway = FakeSessionGateway()
        model = ItmoSignInModel()
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

    func testCallbackPageTokensReachTheSession() async throws {
        try await load(page: "https://my.itmo.ru/login/callback?state=synthetic", script: postThroughBridge)

        await waitUntil { self.model.state.error != nil }
        XCTAssertEqual(errorKey, "auth_error_invalid_credentials")
        XCTAssertFalse(model.state.completingLogin)
        XCTAssertTrue(model.state.showsError)
    }

    func testBridgeExistsOnlyOnTheCallbackPage() async throws {
        try await load(page: "https://my.itmo.ru/login", script: "")
        let type = try await browser.webView.evaluateJavaScript("typeof window.ItmoAuthBridge") as? String
        XCTAssertEqual(type, "undefined")
    }

    func testTokensPostedOffTheCallbackPageAreIgnored() async throws {
        // Messages arrive in order, so the page's load message proves its token message was handled.
        try await load(page: "https://my.itmo.ru/login", script: postDirectly)

        XCTAssertFalse(model.state.completingLogin)
        XCTAssertNil(model.state.error)
    }

    func testTokensFromAnotherOriginAreIgnored() async throws {
        try await load(page: "https://id.itmo.ru/login/callback", script: postDirectly)

        XCTAssertFalse(model.state.completingLogin)
        XCTAssertNil(model.state.error)
    }

    func testOnlyHttpsPagesMayLoadInTheMainFrame() {
        XCTAssertEqual(ItmoAuthUrls.login.absoluteString, "https://my.itmo.ru/")
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

    func testFailedPageStaysFailedUntilRetryWhichLoadsACleanPage() {
        model.mainFrameFailed()
        model.pageStarted()
        model.pageFinished()
        XCTAssertEqual(model.state.page, .failed)
        XCTAssertEqual(model.errorTitle, AppStrings.string("auth_web_error"))

        let generation = model.loadGeneration
        model.retry()
        XCTAssertEqual(model.loadGeneration, generation + 1)
        XCTAssertEqual(model.state.page, .loading)
        model.pageFinished()
        XCTAssertEqual(model.state.page, .shown)
        XCTAssertFalse(model.state.showsError)
    }

    func testTokensWithoutAPageAreIgnored() {
        model.tokensPosted(pageURL: nil, tokenResponseJson: tokenResponse)
        XCTAssertFalse(model.state.completingLogin)
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

    func testGateShowsTheFirstRunFlowOnlyToANewAccount() async {
        let session = ShellSession(gateway: gateway)
        let follow = Task { await session.follow() }
        defer { follow.cancel() }
        await waitUntil { self.gateway.isObserved }

        gateway.emit(SessionStateSignedIn(user: nil, demo: false))
        await waitUntil { session.state == .signedIn }
        XCTAssertEqual(session.surface, .loading, "the flag is not read yet: nothing is guessed")

        let steps: [(OnboardingStatus, ShellSurfaceKind)] = [
            (.required, .onboarding),
            (.passed, .tabs(demo: false)),
        ]
        for (status, expected) in steps {
            gateway.emit(status)
            await waitUntil { session.surface == expected }
        }

        gateway.emit(.required)
        gateway.emit(SessionStateSignedIn(user: nil, demo: true))
        await waitUntil { session.onboarding == .required && session.state == .demo }
        XCTAssertEqual(session.surface, .tabs(demo: true), "the demo skips the flow without passing it")

        gateway.emit(SessionStateSignedOut.shared)
        await waitUntil { session.surface == .auth }
    }

    func testOnboardingPreviewShowsTheFlowOverTheDemoUntilItEnds() async {
        let session = ShellSession(gateway: gateway, previewsOnboarding: true)
        let follow = Task { await session.follow() }
        defer { follow.cancel() }
        await waitUntil { self.gateway.isObserved }

        gateway.emit(SessionStateSignedIn(user: nil, demo: true))
        await waitUntil { session.surface == .onboarding }
        session.onboardingFinished()
        XCTAssertEqual(session.surface, .tabs(demo: true))
    }

    func testGateFlagMapsAsAndroidsShell() {
        XCTAssertEqual(OnboardingStatus(OnboardingGate.unknown), .unknown)
        XCTAssertEqual(OnboardingStatus(OnboardingGate.required), .required)
        XCTAssertEqual(OnboardingStatus(OnboardingGate.passed), .passed)
    }

    func testSignOutGoesThroughTheRepositoryOnce() async {
        let session = ShellSession(gateway: gateway)

        await session.signOut()?.value

        XCTAssertEqual(gateway.signOutCalls, 1)
        XCTAssertEqual(session.state, .loading, "the gate waits for the repository's states")
        session.signIn()
        XCTAssertEqual(session.state, .loading, "the shared session signs in only on the sign-in screen")
    }

    func testFixtureSessionChangesWithoutARepository() async {
        let session = ShellSession(state: .demo)
        await session.follow()
        XCTAssertEqual(session.surface, .tabs(demo: true))
        XCTAssertNil(session.signOut())
        XCTAssertEqual(session.state, .signedOut)
        session.signIn()
        XCTAssertEqual(session.state, .signedIn)
    }

    // MARK: Helpers

    private var errorKey: String? {
        (model.state.error as? UiTextRes)?.resource.key
    }

    private var postThroughBridge: String {
        "window.ItmoAuthBridge.postTokens('\(tokenResponse)');"
    }

    private var postDirectly: String {
        "window.webkit.messageHandlers.\(ItmoTokenBridge.handlerName).postMessage('\(tokenResponse)');"
    }

    /// Loads local HTML as `page` (its origin and URL) that runs `script`, then reports the load through a test
    /// handler, and waits for that report. The wait is the page's own message, so it ends as soon as WebKit has run
    /// the page's scripts, however long a cold web content process takes to start on a loaded CI simulator;
    /// `safetyNet` only bounds a page that never reports.
    private func load(page: String, script: String) async throws {
        let loaded = expectation(description: "\(page) reported its load")
        let reporter = PageLoadReporter(page: page) { loaded.fulfill() }
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
        await fulfillment(of: [loaded], timeout: Self.safetyNet)
        XCTAssertEqual(browser.webView.url?.absoluteString, page)
    }

    /// The upper bound of a wait that ends on its event or condition: never reached by a passing test.
    private static let safetyNet: TimeInterval = 60

    private func waitUntil(timeout: TimeInterval = safetyNet, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(condition(), "the condition did not hold within \(timeout) s")
    }
}

/// `SessionRepository` and the first-run flag as the shell sees them, driven by the test.
@MainActor
private final class FakeSessionGateway: SessionGateway {
    private(set) var signOutCalls = 0
    private var continuation: AsyncStream<SessionState>.Continuation?
    private var onboardingContinuation: AsyncStream<OnboardingStatus>.Continuation?

    var isObserved: Bool { continuation != nil && onboardingContinuation != nil }

    func states() -> AsyncStream<SessionState> {
        let (stream, continuation) = AsyncStream<SessionState>.makeStream()
        self.continuation = continuation
        return stream
    }

    func onboardingStates() -> AsyncStream<OnboardingStatus> {
        let (stream, continuation) = AsyncStream<OnboardingStatus>.makeStream()
        onboardingContinuation = continuation
        return stream
    }

    func emit(_ state: SessionState) {
        continuation?.yield(state)
    }

    func emit(_ status: OnboardingStatus) {
        onboardingContinuation?.yield(status)
    }

    func signOut() async {
        signOutCalls += 1
    }
}

/// Reports, once, that `page` posted its load.
@MainActor
private final class PageLoadReporter: NSObject, WKScriptMessageHandler {
    static let name = "itmoTestPageLoaded"
    private let page: String
    private var onLoad: (() -> Void)?

    init(page: String, onLoad: @escaping () -> Void) {
        self.page = page
        self.onLoad = onLoad
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.body as? String == page else { return }
        onLoad?()
        onLoad = nil
    }
}
