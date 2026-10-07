import SwiftUI
import UIKit
import WebKit

/// The `WKWebView` of the sign-in page on `WKWebsiteDataStore.default()`, the store My ITMO web and the BARS login
/// will share and that sign-out clears (`IosCoreHost.clearWebsiteData`). It reports the page lifecycle to the model,
/// keeps the main frame on `ItmoAuthUrls.isNavigable` pages, opens no windows, and hands posted tokens to the model.
@MainActor
final class ItmoSignInBrowser: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    private let model: ItmoSignInModel
    private var tokenHandler: ItmoTokenMessageHandler?
    private var loadedGeneration: Int?

    init(model: ItmoSignInModel) {
        self.model = model
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()

        let handler = ItmoTokenMessageHandler { [weak model] pageURL, tokenResponseJson in
            model?.tokensPosted(pageURL: pageURL, tokenResponseJson: tokenResponseJson)
        }
        tokenHandler = handler
        ItmoTokenBridge.install(in: configuration.userContentController, handler: handler)
        webView.navigationDelegate = self
        webView.accessibilityIdentifier = "auth.signIn.web"
        // The page area follows the theme until ITMO.ID draws (it is white, and slow to arrive on a cold start).
        webView.isOpaque = false
        webView.backgroundColor = .systemBackground

        let refresh = UIRefreshControl()
        refresh.addTarget(self, action: #selector(pullToRefresh(_:)), for: .valueChanged)
        webView.scrollView.refreshControl = refresh
    }

    /// Loads the sign-in page once per `ItmoSignInModel.loadGeneration`.
    func loadIfNeeded() {
        guard loadedGeneration != model.loadGeneration else { return }
        loadedGeneration = model.loadGeneration
        webView.stopLoading()
        webView.load(URLRequest(url: ItmoAuthUrls.login, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    @objc private func pullToRefresh(_ control: UIRefreshControl) {
        webView.reload()
        control.endRefreshing()
    }

    // MARK: WKNavigationDelegate

    func webView(
        _ webView: WKWebView,
        decidePolicyFor action: WKNavigationAction,
        decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
    ) {
        guard let frame = action.targetFrame else {
            // A new window: Android allows none.
            decisionHandler(.cancel)
            return
        }
        if frame.isMainFrame, !ItmoAuthUrls.isNavigable(action.request.url) {
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        model.pageStarted()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        model.pageFinished()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        mainFrameFailed(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        mainFrameFailed(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    /// A cancelled load (a newer navigation, a cancelled policy decision) is not a failed page.
    private func mainFrameFailed(_ error: Error) {
        let error = error as NSError
        if error.domain == NSURLErrorDomain, error.code == NSURLErrorCancelled { return }
        if error.domain == WKError.errorDomain, error.code == Self.frameLoadInterruptedByPolicyChange { return }
        model.mainFrameFailed()
    }

    /// `WebKitErrorFrameLoadInterruptedByPolicyChange`, which WebKit reports for a navigation the policy cancelled.
    private static let frameLoadInterruptedByPolicyChange = 102
}

/// The browser in SwiftUI: made once per view identity, reloaded when the model asks for a clean page.
struct ItmoSignInWebView: UIViewRepresentable {
    let model: ItmoSignInModel

    func makeCoordinator() -> ItmoSignInBrowser {
        ItmoSignInBrowser(model: model)
    }

    func makeUIView(context: Context) -> WKWebView {
        context.coordinator.loadIfNeeded()
        return context.coordinator.webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // Reading the generation here makes SwiftUI call this again on each retry.
        _ = model.loadGeneration
        context.coordinator.loadIfNeeded()
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: ItmoSignInBrowser) {
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.configuration.userContentController.removeAllScriptMessageHandlers()
    }
}
