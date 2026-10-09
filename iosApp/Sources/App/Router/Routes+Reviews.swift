import Shared
import SwiftUI

extension Routes {
    /// The teacher reviews (IO-09f): the review editor is a Compose sheet above the shell, opened from a teacher's
    /// profile (`ReviewEditorSheet.swift`). The report of a review is the Compose dialog over the profile that asks for
    /// it, so its key has no iOS surface of its own.
    static func reviews(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let editor as AppRoutes.ReviewEditor:
            let args = editor.args
            return .composeSheet { AnyView(ReviewEditorSheetView(args: args)) }
        default:
            return .notOnIOS
        }
    }
}
