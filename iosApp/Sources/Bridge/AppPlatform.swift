import Shared
import UIKit
import WebKit
import WidgetKit

/// The app's `IosPlatform`: what the Kotlin graph needs from UIKit, WidgetKit and WebKit, the BARS
/// session's cookies and hidden view included, and pairing's CryptoKit HPKE and LocalAuthentication.
/// `App.init` passes it to `startKoinIos`; Kotlin calls it on the main thread.
final class AppPlatform: NSObject, IosPlatform {
    func reload(kind: String) {
        WidgetCenter.shared.reloadTimelines(ofKind: kind)
    }

    func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }

    /// WebKit's removal of every website record. Unit tests that sign out through the app's graph replace it: on a CI
    /// simulator the real call also deletes Screen Time web history, which hangs for minutes.
    static var removeWebsiteData: (@escaping () -> Void) -> Void = { completion in
        WKWebsiteDataStore.default().removeData(
            ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
            modifiedSince: .distantPast,
            completionHandler: completion
        )
    }

    func clearWebsiteData(completion: @escaping () -> Void) {
        Self.removeWebsiteData(completion)
    }

    func webKitCookies(completion: @escaping ([WebKitCookie]) -> Void) {
        WebKitCookies.all(completion: completion)
    }

    func loadHiddenBarsPage(
        url: String,
        navigation: BarsWebNavigation,
        completion: @escaping (String?) -> Void
    ) -> any BarsWebLoad {
        HiddenBarsBrowser.load(url: url, navigation: navigation, completion: completion)
    }

    func hpkeSeal(recipientPublicKey: Data, info: Data, plaintext: Data) -> Data? {
        try? PairingHpke.seal(recipientPublicKey: recipientPublicKey, info: info, plaintext: plaintext)
    }

    func canUnlockDevice() -> Bool {
        DeviceUnlocker.canUnlock()
    }

    func unlockDevice(reason: String, completion: @escaping (KotlinBoolean) -> Void) {
        DeviceUnlocker.unlock(reason: reason) { completion(KotlinBoolean(bool: $0)) }
    }

    func installedWidgetKinds(completion: @escaping (Set<String>) -> Void) {
        WidgetCenter.shared.getCurrentConfigurations { result in
            let kinds = Set((try? result.get())?.map(\.kind) ?? [])
            DispatchQueue.main.async { completion(kinds) }
        }
    }
}
