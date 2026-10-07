import Foundation
import Shared
import WebKit

/// The hidden `WKWebView` of the BARS renewal (IO-09d1, 11 row 13): Android's headless `BarsWebSilentLogin` on
/// `WKWebsiteDataStore.default()`, so ITMO.ID's SSO cookies of the sign-in pages sign it in. Never shown. Kotlin's
/// `BarsWebNavigation` decides each main-frame URL: an ITMO.ID page loads, the exact BARS callback is cancelled and
/// handed over, anything else stops the flow; other frames load only over https, no window opens. A page that
/// finishes loading is a sign-in form, so the flow ends without a code. Kotlin's `WebViewBarsSilentLogin` holds the
/// timeout and checks the callback's `state`; nothing here logs a URL.
final class HiddenBarsBrowser: NSObject, BarsWebLoad, WKNavigationDelegate {
    /// Loads in flight: WebKit holds its navigation delegate weakly.
    private static var active: Set<HiddenBarsBrowser> = []

    private let navigation: BarsWebNavigation
    private var completion: ((String?) -> Void)?
    private var webView: WKWebView?

    private init(navigation: BarsWebNavigation, completion: @escaping (String?) -> Void) {
        self.navigation = navigation
        self.completion = completion
        super.init()
    }

    /// Starts one hidden load on the main thread; see `HiddenBarsWebView.loadHiddenBarsPage`.
    static func load(url: String, navigation: BarsWebNavigation, completion: @escaping (String?) -> Void)
        -> HiddenBarsBrowser {
        let browser = HiddenBarsBrowser(navigation: navigation, completion: completion)
        guard let target = URL(string: url) else {
            browser.finish(nil)
            return browser
        }
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = browser
        browser.webView = webView
        active.insert(browser)
        webView.load(URLRequest(url: target, cachePolicy: .reloadIgnoringLocalCacheData))
        return browser
    }

    func cancel() {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { self.cancel() }
            return
        }
        completion = nil
        tearDown()
    }

    private func finish(_ callbackURL: String?) {
        let completion = self.completion
        self.completion = nil
        tearDown()
        completion?(callbackURL)
    }

    private func tearDown() {
        webView?.stopLoading()
        webView?.navigationDelegate = nil
        webView = nil
        Self.active.remove(self)
    }

    // MARK: WKNavigationDelegate

    func webView(
        _ webView: WKWebView,
        decidePolicyFor action: WKNavigationAction,
        decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
    ) {
        guard let frame = action.targetFrame, let url = action.request.url?.absoluteString else {
            decisionHandler(.cancel)
            return
        }
        guard frame.isMainFrame else {
            decisionHandler(HttpsNavigationPolicy.shared.isNavigable(url: url) ? .allow : .cancel)
            return
        }
        switch navigation.step(url: url) {
        case .allow:
            decisionHandler(.allow)
        case .callback:
            decisionHandler(.cancel)
            finish(url)
        case .stop:
            decisionHandler(.cancel)
            finish(nil)
        }
    }

    /// SP-21 saw `decidePolicyFor` catch the callback redirect; this is the fallback it kept.
    func webView(_ webView: WKWebView, didReceiveServerRedirectForProvisionalNavigation navigation: WKNavigation!) {
        guard let url = webView.url?.absoluteString else { return }
        switch self.navigation.step(url: url) {
        case .allow: break
        case .callback: finish(url)
        case .stop: finish(nil)
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // A rendered ITMO.ID page is a sign-in form: only the user can continue from here.
        finish(nil)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        failed(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        failed(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        finish(nil)
    }

    /// A navigation the policy cancelled is not a failed page: the decision already finished the flow or let a
    /// newer navigation run.
    private func failed(_ error: Error) {
        let error = error as NSError
        if error.domain == NSURLErrorDomain, error.code == NSURLErrorCancelled { return }
        if error.domain == WKError.errorDomain, error.code == Self.frameLoadInterruptedByPolicyChange { return }
        finish(nil)
    }

    /// `WebKitErrorFrameLoadInterruptedByPolicyChange`.
    private static let frameLoadInterruptedByPolicyChange = 102
}
