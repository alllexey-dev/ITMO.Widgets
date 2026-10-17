import Shared
import SwiftUI

extension Routes {
    /// The teacher reviews, both opened from a teacher's card: the review editor is a Compose sheet above the shell
    /// (`ReviewEditorSheet.swift`), the report of a review a SwiftUI form sheet (`ReportReviewSheet`).
    static func reviews(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let editor as AppRoutes.ReviewEditor:
            let args = editor.args
            return .composeSheet { AnyView(ReviewEditorSheetView(args: args)) }
        case let report as AppRoutes.ReportReview:
            let args = report.args
            let reviewId = report.reviewId
            return .swiftUISheet { AnyView(ReportReviewSheet(args: args, reviewId: reviewId)) }
        default:
            return .notOnIOS
        }
    }
}
