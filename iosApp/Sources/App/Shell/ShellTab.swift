import Shared

/// A root of the tab bar, in the bar's order (DECISIONS.md, Shell and navigation): today, schedule, recordbook, sport,
/// profile. The raw value is the tab's frozen route id (`itmowidgets://route/<id>`, `StableIdentifiersTests`), which
/// predates the today and profile names.
enum ShellTab: String, CaseIterable, Identifiable, Hashable {
    case today = "home"
    case schedule
    case recordbook
    case sport
    case profile = "me"

    /// The tab selected at launch.
    static let launch: ShellTab = .today

    /// The tabs the bar shows, in order.
    static var visible: [ShellTab] { allCases.filter(\.isAvailable) }

    var id: String { rawValue }

    /// Whether iOS offers the tab: the recordbook follows `PlatformCapabilities.recordbook`, so a
    /// tab without its screen never shows as a placeholder (App Review 2.1).
    var isAvailable: Bool { self != .recordbook || IosPlatformCapabilitiesKt.IosPlatformCapabilities.recordbook }

    /// The shared tab model's name (`AppTab.label`).
    var title: String { AppStrings.string(appTab.label) }

    /// The shared tab model's icon; the bar fills the selected tab's symbol itself.
    var symbol: AppSymbol { appTab.icon.symbol }
}
