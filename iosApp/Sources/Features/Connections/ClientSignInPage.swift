import Shared
import SwiftUI
import WebKit

/// The official ITMO.ID page for a client (UX spec section 5, step 3), full screen: the inline title
/// (`pair_sign_in_title`) over id.itmo.ru with the close button, on a renewal the code to compare above the page. The
/// page runs on its own `WKWebsiteDataStore.nonPersistent()`, so the client's sign-in never touches the app's cookies
/// and leaves nothing behind, with the User-Agent the ViewModel chose. Every main-frame navigation goes to
/// `navigation` first: the callback is the ViewModel's and never loads; anything else loads only over https.
struct ClientSignInPage: View {
    let state: PairingUiStateSigningIn
    let navigation: (String) -> Bool
    let close: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let code = state.code {
                    HStack(spacing: ItmoSpacing.content) {
                        Text(verbatim: AppStrings.string("pair_code_title"))
                            .font(.itmo(.bodyMedium))
                        Spacer(minLength: ItmoSpacing.compact)
                        Text(verbatim: code)
                            .font(.itmo(.titleLarge).monospaced())
                            .accessibilityIdentifier("pairing.signIn.code")
                    }
                    .padding(ItmoSpacing.content)
                    .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: ItmoCorner.medium))
                    .padding(.horizontal, ItmoKit.margin)
                    .padding(.vertical, ItmoSpacing.related)
                    .accessibilityElement(children: .combine)
                }
                ClientSignInWebView(url: state.url, userAgent: state.userAgent, navigation: navigation)
                    .ignoresSafeArea(edges: .bottom)
            }
            .navigationTitle(Text(verbatim: state.title.resolved))
            .navigationSubtitle(Text(verbatim: AppStrings.string("pair_sign_in_host")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(role: .close, action: close)
                        .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                        .accessibilityIdentifier("pairing.signIn.close")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("pairing.signIn")
    }
}

/// The isolated page in SwiftUI: made once, loaded once, emptied when it leaves.
private struct ClientSignInWebView: UIViewRepresentable {
    let url: String
    let userAgent: String
    let navigation: (String) -> Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(navigation: navigation)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        let page = WKWebView(frame: .zero, configuration: configuration)
        page.customUserAgent = userAgent
        page.navigationDelegate = context.coordinator
        page.accessibilityIdentifier = "pairing.signIn.web"
        if HttpsNavigationPolicy.shared.isNavigable(url: url), let target = URL(string: url) {
            page.load(URLRequest(url: target, cachePolicy: .reloadIgnoringLocalCacheData))
        }
        return page
    }

    func updateUIView(_ page: WKWebView, context: Context) {
        context.coordinator.navigation = navigation
    }

    static func dismantleUIView(_ page: WKWebView, coordinator: Coordinator) {
        page.stopLoading()
        page.navigationDelegate = nil
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        var navigation: (String) -> Bool

        init(navigation: @escaping (String) -> Bool) {
            self.navigation = navigation
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor action: WKNavigationAction,
            decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
        ) {
            guard let frame = action.targetFrame else {
                // A new window: the page opens none.
                decisionHandler(.cancel)
                return
            }
            guard frame.isMainFrame else {
                decisionHandler(.allow)
                return
            }
            let target = action.request.url?.absoluteString ?? ""
            if navigation(target) || !HttpsNavigationPolicy.shared.isNavigable(url: target) {
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }
    }
}
