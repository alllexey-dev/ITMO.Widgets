import Shared
import SwiftUI
import WebKit

/// The official ITMO.ID page for a client (UX spec section 5, step 3), full screen: the inline title
/// (`pair_sign_in_title`) over id.itmo.ru with the close button. The page runs on its own
/// `WKWebsiteDataStore.nonPersistent()`, so the client's sign-in never touches the app's cookies and leaves nothing
/// behind, with the User-Agent the ViewModel chose. Every main-frame navigation goes to `isCallback` first: the
/// callback is the ViewModel's and never loads, it goes to `callback` with the store's `KEYCLOAK_IDENTITY` when the
/// state asks for it (`readIdentity`, an `itmo:sso` grant); anything else loads only over https.
struct ClientSignInPage: View {
    let state: PairingUiStateSigningIn
    let isCallback: (String) -> Bool
    let callback: (String, String?) -> Void
    let close: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ClientSignInWebView(
                    url: state.url,
                    userAgent: state.userAgent,
                    readIdentity: state.readIdentity,
                    isCallback: isCallback,
                    callback: callback
                )
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
    let readIdentity: Bool
    let isCallback: (String) -> Bool
    let callback: (String, String?) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(readIdentity: readIdentity, isCallback: isCallback, callback: callback)
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
        context.coordinator.readIdentity = readIdentity
        context.coordinator.isCallback = isCallback
        context.coordinator.callback = callback
    }

    static func dismantleUIView(_ page: WKWebView, coordinator: Coordinator) {
        page.stopLoading()
        page.navigationDelegate = nil
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        var readIdentity: Bool
        var isCallback: (String) -> Bool
        var callback: (String, String?) -> Void

        init(readIdentity: Bool, isCallback: @escaping (String) -> Bool, callback: @escaping (String, String?) -> Void) {
            self.readIdentity = readIdentity
            self.isCallback = isCallback
            self.callback = callback
        }

        /// The callback never loads. `KEYCLOAK_IDENTITY` of id.itmo.ru is read from the page's own store first when
        /// asked; the closure keeps the store alive until it answers. The value is never logged.
        private func catchCallback(_ target: String, in page: WKWebView) {
            guard readIdentity else { return callback(target, nil) }
            let store = page.configuration.websiteDataStore.httpCookieStore
            store.getAllCookies { [callback] cookies in
                let identity = ItmoIdentityCookie.value(in: cookies)
                DispatchQueue.main.async { callback(target, identity) }
            }
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
            if isCallback(target) {
                decisionHandler(.cancel)
                catchCallback(target, in: webView)
            } else if !HttpsNavigationPolicy.shared.isNavigable(url: target) {
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }
    }
}

/// `KEYCLOAK_IDENTITY` of id.itmo.ru among a store's cookies (`KeycloakIdentityCookie` of the shared code): the exact
/// name on the realm's host, with or without the leading dot of a domain cookie. Never logged.
enum ItmoIdentityCookie {
    static func value(in cookies: [HTTPCookie]) -> String? {
        cookies.first { cookie in
            cookie.name == KeycloakIdentityCookie.shared.NAME
                && cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")) == KeycloakIdentityCookie.shared.DOMAIN
        }?.value
    }
}
