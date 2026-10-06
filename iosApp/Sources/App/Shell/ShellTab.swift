/// A root of the tab bar, in the order of Android's `res/menu/bottom_nav.xml`.
enum ShellTab: String, CaseIterable, Identifiable, Hashable {
    case recordbook
    case schedule
    case home
    case sport
    case me

    /// The tab selected at launch, as on Android.
    static let launch: ShellTab = .home

    /// The tabs the bar shows, in order.
    static var visible: [ShellTab] { allCases.filter(\.isAvailable) }

    var id: String { rawValue }

    /// The recordbook has no screen on iOS until IO-09d2; App Review rejects placeholders, so it has no entry point.
    var isAvailable: Bool { self != .recordbook }

    var title: String { AppStrings.string(titleKey) }

    var symbol: AppSymbol {
        switch self {
        case .recordbook: .menuBook
        case .schedule: .schedule
        case .home: .home
        case .sport: .exercise
        case .me: .accountCircle
        }
    }

    var selectedSymbol: AppSymbol {
        switch self {
        case .recordbook: .menuBookFilled
        case .schedule: .scheduleFilled
        case .home: .homeFilled
        case .sport: .exerciseFilled
        case .me: .accountCircleFilled
        }
    }

    private var titleKey: String { "title_\(rawValue)" }
}
