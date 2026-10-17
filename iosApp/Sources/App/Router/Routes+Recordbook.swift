import Shared
import SwiftUI

extension Routes {
    /// The recordbook root (`RecordbookScreen`); the subject page is a SwiftUI screen pushed on the selected tab's
    /// stack, the own sheet scores a SwiftUI sheet above the shell.
    static func recordbook(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let subject as AppRoutes.RecordbookSubject:
            guard let args = subject.args.validOrNull() else { return .notOnIOS }
            return .swiftUI { AnyView(RecordbookSubjectScreen(args: args)) }
        case let scores as AppRoutes.SheetScores:
            let args = scores.args
            return .swiftUISheet { AnyView(SheetScoresSheetView(scores: args)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
