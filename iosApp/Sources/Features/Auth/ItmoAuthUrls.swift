import Foundation
import Shared

/// Android's `ItmoAuthUrlPolicy` for the sign-in page. Navigation is the shared `HttpsNavigationPolicy` (any https
/// page: ITMO.ID hands over to VK and other providers on their own hosts). The token callback check is its exact
/// copy on the shared `StrictUri`, since `Shared` exports only `:shared:core` until IO-07b moves the page onto
/// `InteractiveLoginViewModel`.
enum ItmoAuthUrls {
    static let login = URL(string: "https://my.itmo.ru/")!

    private static let myItmoHost = "my.itmo.ru"
    private static let callbackPath = "/login/callback"

    static func isNavigable(_ url: URL?) -> Bool {
        guard let url else { return false }
        return HttpsNavigationPolicy.shared.isNavigable(url: url.absoluteString)
    }

    /// The only page whose posted tokens count: `https://my.itmo.ru/login/callback`, any query.
    static func isTokenCallback(_ url: URL?) -> Bool {
        guard let url, let uri = StrictUri.companion.parse(text: url.absoluteString) else { return false }
        return uri.scheme?.lowercased() == "https" && uri.host?.lowercased() == myItmoHost && uri.path == callbackPath
    }

    /// The origin a token message must come from: the main frame of `https://my.itmo.ru`.
    static func isMyItmoOrigin(protocol scheme: String, host: String, port: Int) -> Bool {
        scheme.lowercased() == "https" && host.lowercased() == myItmoHost && (port == 0 || port == 443)
    }
}
