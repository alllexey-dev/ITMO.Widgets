import UIKit

/// The Home Screen quick actions: static `UIApplicationShortcutItems` in the app's Info.plist whose type is
/// `$(PRODUCT_BUNDLE_IDENTIFIER).<route id>` (`dev.alllexey.itmowidgets.qr_pass`, `.today`, Android's shortcut ids)
/// and whose title is a catalog key that `InfoPlist.xcstrings` resolves.
enum QuickActions {
    /// The route of a quick action; `nil` for a type this build does not know.
    static func route(
        of item: UIApplicationShortcutItem, bundleID: String? = Bundle.main.bundleIdentifier
    ) -> IntentRoute? {
        guard let bundleID, item.type.hasPrefix(bundleID + ".") else { return nil }
        return IntentRoute(rawValue: String(item.type.dropFirst(bundleID.count + 1)))
    }

    /// Offers the action's route to the router; `false` for an unknown type.
    @MainActor
    @discardableResult
    static func perform(_ item: UIApplicationShortcutItem) -> Bool {
        route(of: item)?.open() ?? false
    }
}

/// Receives the quick action that launched the app (`connectionOptions`) and installs `QuickActionSceneDelegate` for
/// the ones that arrive while it runs. SwiftUI keeps managing the window.
final class ITMOWidgetsAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let item = options.shortcutItem {
            QuickActions.perform(item)
        }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }
}

/// A quick action chosen while the app is in memory.
final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(QuickActions.perform(shortcutItem))
    }
}
