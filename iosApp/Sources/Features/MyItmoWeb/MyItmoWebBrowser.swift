import Foundation
import Observation
import Shared
import UIKit
import WebKit

/// What the My ITMO page shows around its browser, Android's `MyItmoWebState`.
enum MyItmoWebLoad: Equatable {
    /// A page loads: the progress line runs over the visible page.
    case loading
    /// The page is loaded: the browser alone.
    case shown
    /// A main frame failed or left the official origins: the error covers the browser.
    case failed
}

/// What the browser does with one navigation, from the shared `MyItmoWebPolicy`.
enum MyItmoWebDecision: Equatable {
    /// An official HTTPS page (`my.itmo.ru`, `id.itmo.ru`) loads inside.
    case load
    /// Another HTTPS page the user tapped opens outside the app.
    case openExternally(URL)
    /// Anything else, redirects and frames that try to leave included; a blocked main frame shows the error.
    case block(showsError: Bool)

    /// `targetFrame` nil (a new window) counts as the main frame: the page stays in this one browser.
    static func of(url: URL?, isMainFrame: Bool, userGesture: Bool) -> MyItmoWebDecision {
        guard let url else { return .block(showsError: isMainFrame) }
        let navigation = MyItmoWebPolicy.shared.navigation(
            url: url.absoluteString,
            mainFrame: isMainFrame,
            userGesture: userGesture
        )
        switch navigation {
        case .internal: return .load
        case .external: return .openExternally(url)
        case .blocked: return .block(showsError: isMainFrame)
        }
    }
}

/// The `WKWebView` of the My ITMO page on `WKWebsiteDataStore.default()`, the store the ITMO.ID sign-in uses and that
/// sign-out clears (`IosCoreHost.clearWebsiteData`): the website keeps its own session, the app injects no token,
/// adds no script bridge and logs no console. Every navigation goes through `MyItmoWebDecision`; the last trusted
/// page is what a reload or a retry loads.
@MainActor
@Observable
final class MyItmoWebBrowser: NSObject, WKNavigationDelegate, WKUIDelegate {
    private(set) var load: MyItmoWebLoad = .loading
    /// The page's load progress, 0 to 1, while `load` is `.loading`.
    private(set) var progress: Double = 0

    @ObservationIgnored let webView: WKWebView
    @ObservationIgnored private var lastTrustedURL = URL(string: MyItmoWebPolicy.shared.HOME_URL)!
    @ObservationIgnored private var progressObservation: NSKeyValueObservation?
    @ObservationIgnored private var started = false
    /// Opens a page outside the app; the screen sets it to SwiftUI's `openURL`.
    @ObservationIgnored var openExternally: (URL) -> Void = { UIApplication.shared.open($0) }

    override init() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.accessibilityIdentifier = "myItmoWeb.page"
        // The page area follows the theme until my.itmo.ru draws.
        webView.isOpaque = false
        webView.backgroundColor = .systemBackground
        progressObservation = webView.observe(\.estimatedProgress, options: [.new]) { [weak self] view, _ in
            let value = view.estimatedProgress
            Task { @MainActor in self?.progress = value }
        }
    }

    /// Loads the home page the first time the screen shows.
    func start() {
        guard !started else { return }
        started = true
        reload()
    }

    /// `my_itmo_web_reload` and `common_retry`: the last trusted page again.
    func reload() {
        load = .loading
        if webView.url == lastTrustedURL {
            webView.reload()
        } else {
            webView.load(URLRequest(url: lastTrustedURL))
        }
    }

    // MARK: WKNavigationDelegate

    func webView(
        _ webView: WKWebView,
        decidePolicyFor action: WKNavigationAction,
        decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
    ) {
        let isMainFrame = action.targetFrame?.isMainFrame ?? true
        let decision = MyItmoWebDecision.of(
            url: action.request.url,
            isMainFrame: isMainFrame,
            userGesture: action.navigationType == .linkActivated
        )
        switch decision {
        case .load where action.targetFrame == nil:
            // A new window: the page opens here instead.
            decisionHandler(.cancel)
            webView.load(action.request)
        case .load:
            decisionHandler(.allow)
        case let .openExternally(url):
            decisionHandler(.cancel)
            openExternally(url)
        case let .block(showsError):
            decisionHandler(.cancel)
            if showsError { load = .failed }
        }
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor response: WKNavigationResponse,
        decisionHandler: @escaping @MainActor (WKNavigationResponsePolicy) -> Void
    ) {
        // As Android's onReceivedHttpError: a failed official page shows the error, not the server's page.
        if response.isForMainFrame, let http = response.response as? HTTPURLResponse, http.statusCode >= 400 {
            decisionHandler(.cancel)
            load = .failed
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        guard let url = webView.url, MyItmoWebPolicy.shared.isInternal(url: url.absoluteString) else {
            webView.stopLoading()
            load = .failed
            return
        }
        lastTrustedURL = url
        // A failure stays until a reload: a late callback must not hide the error.
        if load != .failed { load = .loading }
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        if let url = webView.url, MyItmoWebPolicy.shared.isInternal(url: url.absoluteString) {
            lastTrustedURL = url
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if load != .failed { load = .shown }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        mainFrameFailed(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        mainFrameFailed(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        reload()
    }

    // MARK: WKUIDelegate

    /// No second window: a `target="_blank"` page already loads here (`decidePolicyFor`).
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        nil
    }

    /// A cancelled load (a newer navigation, a policy decision) is not a failed page.
    private func mainFrameFailed(_ error: Error) {
        let error = error as NSError
        if error.domain == NSURLErrorDomain, error.code == NSURLErrorCancelled { return }
        if error.domain == WKError.errorDomain, error.code == Self.frameLoadInterruptedByPolicyChange { return }
        load = .failed
    }

    /// `WebKitErrorFrameLoadInterruptedByPolicyChange`, which WebKit reports for a navigation the policy cancelled.
    private static let frameLoadInterruptedByPolicyChange = 102
}
