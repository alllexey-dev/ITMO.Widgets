import Observation
import Shared
import SwiftUI
import WebKit

/// The BARS sign-in (IO-09d1), Android's `BarsLoginActivity` as a sheet over the shared `BarsLoginViewModel`: the
/// official BARS authorization page in a `WKWebView` on `WKWebsiteDataStore.default()`, where ITMO.ID's SSO session
/// usually signs in at once. Only the exact HTTPS callback is consumed (ADR 0012); its `state` is checked in Kotlin.
/// A failed page or sign-in shows an error with a retry, which also clears WebKit's cookies so another ITMO.ID account
/// can sign in, as on Android. Leaving the sheet copies the ITMO.ID cookies to the Keychain (`ItmoIdCookieExport`).
/// `onSignedIn` runs once the session is stored; the caller dismisses the sheet.
struct BarsLoginSheet: View {
    @State private var model = ObservableViewModel<BarsLoginViewModel, BarsLoginUiState>(state: \.uiState) { store in
        guard let model = store.resolve(
            type: BarsLoginViewModel.self,
            parameters: BarsLoginParameters.shared.fresh()
        ) as? BarsLoginViewModel else {
            preconditionFailure("Koin resolved no BarsLoginViewModel")
        }
        return model
    }
    @State private var page = BarsLoginPage()
    @Environment(\.dismiss) private var dismiss

    let onSignedIn: () -> Void

    var body: some View {
        NavigationStack {
            content(model.state)
                .navigationTitle(Text(verbatim: AppStrings.string("recordbook_bars_login")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(AppStrings.string("common_close")) { dismiss() }
                            .accessibilityIdentifier("bars.login.close")
                    }
                }
        }
        .observing(model)
        .onEvents(of: model, \.events) { _ in onSignedIn() }
        .onDisappear(perform: Self.copyCookies)
        .accessibilityIdentifier("bars.login")
    }

    @ViewBuilder
    private func content(_ state: BarsLoginUiState) -> some View {
        ZStack {
            BarsLoginWebView(viewModel: model.viewModel, page: page)
                .opacity(showsError(state) || state.completing ? 0 : 1)
                .accessibilityHidden(showsError(state) || state.completing)
            if showsError(state) {
                ItmoErrorView(title: errorTitle(state), retry: retry)
                    .frame(maxHeight: .infinity)
                    .background(Color(uiColor: .systemBackground))
                    .accessibilityIdentifier("bars.login.error")
            } else if state.completing {
                ItmoLoadingView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(uiColor: .systemBackground))
                    .accessibilityIdentifier("bars.login.completing")
            }
        }
        .background(Color(uiColor: .systemBackground))
    }

    private func showsError(_ state: BarsLoginUiState) -> Bool {
        state.error != nil || page.failed
    }

    /// Android's texts: another BARS account, the error's own text, or the page that did not open.
    private func errorTitle(_ state: BarsLoginUiState) -> String {
        guard let error = state.error else { return AppStrings.string("auth_web_error") }
        switch onEnum(of: error) {
        case .forbidden: return AppStrings.string("recordbook_bars_wrong_account")
        default: return AppErrorTextsKt.toUiText(error).resolved
        }
    }

    private func retry() {
        model.viewModel.retry()
        WKWebsiteDataStore.default().removeData(
            ofTypes: [WKWebsiteDataTypeCookies],
            modifiedSince: .distantPast
        ) { [page] in
            page.reload()
        }
    }

    private static func copyCookies() {
        guard let export = IosKoin.shared.get(type: ItmoIdCookieExport.self) as? ItmoIdCookieExport else { return }
        Task { try? await export.run() }
    }
}

/// The page lifecycle the browser reports, apart from the ViewModel's sign-in state.
@MainActor
@Observable
final class BarsLoginPage {
    /// The main frame failed or left the allowed pages; it stays failed until `reload()`.
    private(set) var failed = false
    /// Grows with each `reload()`; the browser loads the sign-in page again when it changes.
    private(set) var generation = 0

    func mainFrameFailed() {
        failed = true
    }

    func reload() {
        failed = false
        generation += 1
    }
}

/// The sheet's browser, made once per view identity.
private struct BarsLoginWebView: UIViewRepresentable {
    let viewModel: BarsLoginViewModel
    let page: BarsLoginPage

    func makeCoordinator() -> BarsLoginBrowser {
        BarsLoginBrowser(viewModel: viewModel, page: page)
    }

    func makeUIView(context: Context) -> WKWebView {
        context.coordinator.loadIfNeeded()
        return context.coordinator.webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // Reading the generation here makes SwiftUI call this again on each retry.
        _ = page.generation
        context.coordinator.loadIfNeeded()
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: BarsLoginBrowser) {
        webView.stopLoading()
        webView.navigationDelegate = nil
    }
}

/// Android's `BarsLoginActivity` browser: https pages only, no new windows, the exact callback handed to the
/// ViewModel and never loaded.
@MainActor
private final class BarsLoginBrowser: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    private let viewModel: BarsLoginViewModel
    private let page: BarsLoginPage
    private var loadedGeneration: Int?

    init(viewModel: BarsLoginViewModel, page: BarsLoginPage) {
        self.viewModel = viewModel
        self.page = page
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.accessibilityIdentifier = "bars.login.web"
        webView.isOpaque = false
        webView.backgroundColor = .systemBackground
    }

    /// Loads the sign-in page once per `BarsLoginPage.generation`, with the ViewModel's current `state`.
    func loadIfNeeded() {
        guard loadedGeneration != page.generation, !viewModel.uiState.value.completing else { return }
        loadedGeneration = page.generation
        guard let url = URL(string: viewModel.loginUrl) else {
            page.mainFrameFailed()
            return
        }
        webView.stopLoading()
        webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
    }

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
            decisionHandler(viewModel.isNavigable(url: url) ? .allow : .cancel)
            return
        }
        if viewModel.isCallback(url: url) {
            decisionHandler(.cancel)
            viewModel.complete(url: url)
        } else if viewModel.isNavigable(url: url) {
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
            page.mainFrameFailed()
        }
    }

    func webView(_ webView: WKWebView, didReceiveServerRedirectForProvisionalNavigation navigation: WKNavigation!) {
        guard let url = webView.url?.absoluteString, viewModel.isCallback(url: url) else { return }
        webView.stopLoading()
        viewModel.complete(url: url)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        failed(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        failed(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    private func failed(_ error: Error) {
        let error = error as NSError
        if error.domain == NSURLErrorDomain, error.code == NSURLErrorCancelled { return }
        if error.domain == WKError.errorDomain, error.code == Self.frameLoadInterruptedByPolicyChange { return }
        page.mainFrameFailed()
    }

    /// `WebKitErrorFrameLoadInterruptedByPolicyChange`.
    private static let frameLoadInterruptedByPolicyChange = 102
}
