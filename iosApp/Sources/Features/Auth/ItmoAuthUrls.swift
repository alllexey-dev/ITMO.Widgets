import Foundation
import Shared

/// Android's `ItmoAuthUrlPolicy` for the sign-in page, on `URL`: the page it opens, the main-frame navigation it
/// allows (any https page: ITMO.ID hands over to VK and other providers on their own hosts) and the token callback.
enum ItmoAuthUrls {
    static let login = URL(string: ItmoAuthUrlPolicy.shared.LOGIN_URL)!

    private static let myItmoHost = "my.itmo.ru"

    static func isNavigable(_ url: URL?) -> Bool {
        guard let url else { return false }
        return ItmoAuthUrlPolicy.shared.isNavigable(url: url.absoluteString)
    }

    /// The only page whose posted tokens count: `https://my.itmo.ru/login/callback`, any query.
    static func isTokenCallback(_ url: URL?) -> Bool {
        guard let url else { return false }
        return ItmoAuthUrlPolicy.shared.isTokenCallback(url: url.absoluteString)
    }

    /// The origin a token message must come from: the main frame of `https://my.itmo.ru`.
    static func isMyItmoOrigin(protocol scheme: String, host: String, port: Int) -> Bool {
        scheme.lowercased() == "https" && host.lowercased() == myItmoHost && (port == 0 || port == 443)
    }
}
