import Foundation
import Shared

/// Catalog text in Swift (ADR 0028, the L05 TC-16b contract). A key is looked up in `Localizable` first, then in the
/// file tables (`strings_<area>`); keys are unique across the catalog, so the first hit is the text.
///
/// - A plural takes its count as argument 1: the forms show it as `%1$lld`, or the `count` substitution selects the
///   form when they show no number, so the count is passed even without arguments.
/// - `%lld` takes `Int64`, `%@` a `String`; a `UiText` argument is resolved first.
/// - Formatting uses the Russian locale, so plural rules never follow an English device.
enum AppStrings {
    static func string(_ key: String, _ arguments: [Any] = [], bundle: Bundle = .main) -> String {
        format(template(key, bundle: bundle), arguments)
    }

    static func plural(_ key: String, count: Int, _ arguments: [Any] = [], bundle: Bundle = .main) -> String {
        format(template(key, bundle: bundle), arguments.isEmpty ? [count] : arguments)
    }

    /// The development language of every bundle (`CFBundleDevelopmentRegion`), the only localization there is.
    static let locale = Locale(identifier: "ru")

    private static let localizable = "Localizable"

    /// Tables that hold no catalog keys: Info.plist values and App Shortcuts phrases.
    private static let systemTables: Set<String> = ["InfoPlist", "AppShortcuts"]

    private static let missing = "\u{0}missing"

    private static var tablesByBundle: [URL: [String]] = [:]
    private static let lock = NSLock()

    private static func template(_ key: String, bundle: Bundle) -> String {
        for table in tables(of: bundle) {
            let text = bundle.localizedString(forKey: key, value: missing, table: table)
            if text != missing { return text }
        }
        assertionFailure("No string table of \(bundle.bundleURL.lastPathComponent) has the key \(key)")
        return key
    }

    /// `Localizable`, then the other compiled tables of [bundle] by name.
    private static func tables(of bundle: Bundle) -> [String] {
        lock.lock()
        defer { lock.unlock() }
        if let tables = tablesByBundle[bundle.bundleURL] { return tables }
        let names = Set(
            ["strings", "stringsdict"].flatMap { type in
                bundle.paths(forResourcesOfType: type, inDirectory: nil, forLocalization: locale.identifier)
            }.map { URL(fileURLWithPath: $0).deletingPathExtension().lastPathComponent }
        )
        let tables = [localizable] + names.subtracting(systemTables).subtracting([localizable]).sorted()
        tablesByBundle[bundle.bundleURL] = tables
        return tables
    }

    private static func format(_ template: String, _ arguments: [Any]) -> String {
        arguments.isEmpty ? template : String(format: template, locale: locale, arguments: arguments.map(formatArgument))
    }

    private static func formatArgument(_ argument: Any) -> CVarArg {
        switch argument {
        case let text as UiText: text.resolved
        case let string as String: string
        // Kotlin numbers arrive as NSNumber subclasses; Swift integers as Int.
        case let number as NSNumber: number.int64Value
        case let number as Int: Int64(number)
        default: String(describing: argument)
        }
    }
}

extension UiText {
    /// The text from this bundle's string tables, as `UiText.resolve()` gives it in Kotlin.
    var resolved: String {
        switch self {
        case let text as UiTextRes:
            AppStrings.string(text.resource.key, text.arguments)
        case let text as UiTextPlural:
            AppStrings.plural(text.resource.key, count: Int(text.count), text.arguments)
        case let text as UiTextDynamic:
            text.value
        default:
            preconditionFailure("\(type(of: self)) holds an Android resource id; port the feature to UiText.Res")
        }
    }
}
