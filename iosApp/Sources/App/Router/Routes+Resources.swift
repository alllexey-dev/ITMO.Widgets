import Shared
import SwiftUI

extension Routes {
    /// The subject links (IO-09f): all links, the link editor and a link's actions are Compose sheets above the shell,
    /// opened from the subject page (`LinkSheets.swift`). The report of a link is the Compose dialog inside the
    /// actions sheet that asks for it, so its key has no iOS surface of its own.
    static func resources(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let links as AppRoutes.SubjectLinks:
            let args = links.args
            return .composeSheet { AnyView(SubjectLinksSheetView(args: args)) }
        case let editor as AppRoutes.LinkEditor:
            return .composeSheet { AnyView(LinkEditorSheetView(key: editor)) }
        case let actions as AppRoutes.LinkActions:
            return .composeSheet { AnyView(LinkActionsSheetView(key: actions)) }
        default:
            return .notOnIOS
        }
    }
}
