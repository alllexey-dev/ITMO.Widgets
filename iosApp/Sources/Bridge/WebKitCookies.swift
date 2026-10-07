import Foundation
import Shared
import WebKit

/// WebKit's cookie store as the Kotlin BARS session reads it (`WebKitCookieSource`, IO-09d1): every cookie of
/// `WKWebsiteDataStore.default()`, the store of the sign-in pages and the hidden BARS view, copied field by field.
/// `ItmoIdCookieExport` keeps only the `id.itmo.ru` ones in the Keychain; nothing here logs a value. Kotlin calls it on
/// the main thread, and WebKit answers there.
enum WebKitCookies {
    static func all(completion: @escaping ([WebKitCookie]) -> Void) {
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
            completion(cookies.map(copy))
        }
    }

    static func copy(_ cookie: HTTPCookie) -> WebKitCookie {
        WebKitCookie(
            name: cookie.name,
            value: cookie.value,
            domain: cookie.domain,
            path: cookie.path,
            secure: cookie.isSecure,
            expiresAtEpochMillis: cookie.expiresDate.map { KotlinLong(value: Int64($0.timeIntervalSince1970 * 1000)) }
        )
    }
}
