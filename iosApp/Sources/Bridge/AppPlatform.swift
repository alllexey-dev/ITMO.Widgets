import Shared
import UIKit
import WebKit
import WidgetKit

/// The app's `IosPlatform` (L18 IO-05): what the Kotlin graph needs from UIKit, WidgetKit and WebKit. `App.init`
/// passes it to `startKoinIos`; Kotlin calls it on the main thread.
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

    func clearWebsiteData(completion: @escaping () -> Void) {
        WKWebsiteDataStore.default().removeData(
            ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
            modifiedSince: .distantPast,
            completionHandler: completion
        )
    }

    func installedWidgetKinds(completion: @escaping (Set<String>) -> Void) {
        WidgetCenter.shared.getCurrentConfigurations { result in
            let kinds = Set((try? result.get())?.map(\.kind) ?? [])
            DispatchQueue.main.async { completion(kinds) }
        }
    }
}
