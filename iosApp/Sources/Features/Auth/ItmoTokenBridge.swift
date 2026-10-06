import Foundation
import WebKit

/// SP-21 path (a): Android's `token_refresh_interceptor.js`, bundled by path from `app/src/main/assets`
/// (`project.yml`), reports the ITMO.ID token response through `window.ItmoAuthBridge.postTokens`, which a script
/// message handler takes here, as `LoginActivity.ItmoAuthBridge.postTokens` does on Android.
enum ItmoTokenBridge {
    /// The script message handler's name: `window.webkit.messageHandlers.postTokens`.
    static let handlerName = "postTokens"

    /// The bundled interceptor, the file Android injects.
    static let interceptorResource = "token_refresh_interceptor"

    /// Defines `window.ItmoAuthBridge` only on the callback page, where Android injects the interceptor, so no other
    /// page has a bridge to post through. It forwards the response text unchanged.
    static let bridgeSource = """
    (function () {
        'use strict';
        if (location.protocol !== 'https:' || location.host !== 'my.itmo.ru' ||
            location.pathname !== '/login/callback') {
            return;
        }
        const handler = window.webkit.messageHandlers.\(handlerName);
        window.ItmoAuthBridge = Object.freeze({
            postTokens: function (tokenResponseJson) {
                handler.postMessage(String(tokenResponseJson));
            }
        });
    })();
    """

    static func interceptorSource(bundle: Bundle = .main) -> String? {
        guard let url = bundle.url(forResource: interceptorResource, withExtension: "js") else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    /// Both scripts at document start in the main frame, the bridge first, and the handler behind a weak proxy:
    /// the content controller retains its handlers.
    static func install(in controller: WKUserContentController, handler: ItmoTokenMessageHandler) {
        controller.addUserScript(WKUserScript(source: bridgeSource, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        if let interceptor = interceptorSource() {
            controller.addUserScript(
                WKUserScript(source: interceptor, injectionTime: .atDocumentStart, forMainFrameOnly: true)
            )
        } else {
            assertionFailure("\(interceptorResource).js is missing from the app bundle")
        }
        controller.add(WeakScriptMessageHandler(handler), name: handlerName)
    }
}

/// Takes the tokens the callback page posts. A message counts only from the main frame of `https://my.itmo.ru` while
/// the page is the callback (`ItmoAuthUrls.isTokenCallback`, checked again by the model) and only as a string; the
/// value is never logged.
@MainActor
final class ItmoTokenMessageHandler: NSObject, WKScriptMessageHandler {
    private let onTokens: @MainActor (_ pageURL: URL?, _ tokenResponseJson: String) -> Void

    init(onTokens: @escaping @MainActor (_ pageURL: URL?, _ tokenResponseJson: String) -> Void) {
        self.onTokens = onTokens
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        let origin = message.frameInfo.securityOrigin
        guard message.name == ItmoTokenBridge.handlerName,
              message.frameInfo.isMainFrame,
              ItmoAuthUrls.isMyItmoOrigin(protocol: origin.protocol, host: origin.host, port: origin.port),
              let tokenResponseJson = message.body as? String
        else { return }
        onTokens(message.webView?.url, tokenResponseJson)
    }
}

/// Holds a script message handler weakly, so the web view's content controller does not keep the page alive.
private final class WeakScriptMessageHandler: NSObject, WKScriptMessageHandler {
    private weak var target: WKScriptMessageHandler?

    init(_ target: WKScriptMessageHandler) {
        self.target = target
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(controller, didReceive: message)
    }
}
