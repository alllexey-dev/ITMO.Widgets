import CoreGraphics

/// Widget family sizes in points on the pinned simulator (iPhone 17, scripts/ios/env.sh), the sizes WidgetKit gives
/// the entry views there. Snapshot tests render entry views at these sizes, never at the matrix width.
enum WidgetSizes {
    static let small = CGSize(width: 170, height: 170)
    static let medium = CGSize(width: 364, height: 170)
    static let large = CGSize(width: 364, height: 382)
}
