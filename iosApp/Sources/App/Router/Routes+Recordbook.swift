import Shared
import SwiftUI

extension Routes {
    /// The recordbook root (`RecordbookTabScreen`); the subject page is a Compose route pushed on the selected tab's
    /// stack, the period picker and the scores sheet Compose sheets above the shell (IO-09d2).
    static func recordbook(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let subject as AppRoutes.RecordbookSubject:
            guard let args = subject.args.validOrNull() else { return .notOnIOS }
            return .compose { AnyView(RecordbookSubjectView(args: args)) }
        case let period as AppRoutes.RecordbookPeriod:
            return .composeSheet { AnyView(RecordbookPeriodSheetView(period: period)) }
        case let scores as AppRoutes.SheetScores:
            let args = scores.args
            return .composeSheet { AnyView(SheetScoresSheetView(scores: args)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
