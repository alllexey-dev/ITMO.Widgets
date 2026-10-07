import CoreGraphics

/// Widget family sizes in points on the pinned simulator (iPhone 17, scripts/ios/env.sh), the sizes WidgetKit gives
/// the entry views there. Snapshot tests render entry views at these sizes, never at the matrix width.
enum WidgetSizes {
    static let small = CGSize(width: 170, height: 170)
    static let medium = CGSize(width: 364, height: 170)
    static let large = CGSize(width: 364, height: 382)

    /// The narrowest phone iOS 18 runs on (iPhone SE, 375 pt wide): the 321 pt medium family is where long names
    /// wrap and truncate first.
    static let narrowMedium = CGSize(width: 321, height: 148)

    /// Lock Screen families, about their size on the pinned simulator; the system renders them in its vibrant style,
    /// the references show the layout.
    static let accessoryRectangular = CGSize(width: 160, height: 72)
    static let accessoryInline = CGSize(width: 234, height: 26)
}
