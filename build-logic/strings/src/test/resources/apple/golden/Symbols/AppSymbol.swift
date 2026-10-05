// Generated from docs/design/icons.tsv by `scripts/verify.sh run -- :app:exportAppleStrings`; do not edit.

/// The icon registry on SwiftUI and system surfaces.
enum AppSymbol: String, CaseIterable {
    case accountCircleFilled = "account_circle_filled"
    case brandGithub = "brand_github"
    case clock = "clock"
    case `repeat` = "repeat"

    /// The SF Symbol name, or the asset name of a custom symbol image when `isCustom`.
    var systemName: String {
        switch self {
        case .accountCircleFilled: "person.crop.circle.fill"
        case .brandGithub: "custom.brand_github"
        case .clock: "clock.fill"
        case .repeat: "repeat"
        }
    }

    /// Custom symbols load with `UIImage(named:)` and `Image(_:)`, not with `systemName:`.
    var isCustom: Bool {
        systemName.hasPrefix("custom.")
    }
}
