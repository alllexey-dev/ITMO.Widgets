import Shared
import SwiftUI

extension AppIcon {
    /// The row of this icon in `docs/design/icons.tsv` on SwiftUI and system surfaces.
    var symbol: AppSymbol {
        guard let symbol = AppSymbol(rawValue: id) else {
            preconditionFailure("AppIcon \(id) has no shared or custom row in docs/design/icons.tsv")
        }
        return symbol
    }
}

extension AppSymbol {
    /// An SF Symbol, or the symbol image of `Brands.xcassets` for a custom row.
    var image: Image {
        isCustom ? Image(systemName) : Image(systemName: systemName)
    }
}
