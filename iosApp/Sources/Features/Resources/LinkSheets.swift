import Shared
import SwiftUI
import UIKit

/// All links of a subject period (`AppRoutes.SubjectLinks`, from the subject page): the Compose sheet content
/// (`subjectLinksViewController`, IO-09f). A link's actions and the editor show over the list, as Android stacks them
/// over its sheet; there each replaces the other, and the scores sheet replaces them, since a links sheet that leads
/// on closes itself first.
struct SubjectLinksSheetView: View {
    let args: SubjectLinksArgs
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var host = LinkSheetsHost()

    var body: some View {
        ComposeHost { [args, host, dismiss] in
            subjectLinksViewController(
                args: args,
                open: { route in host.show(route) },
                linkFailed: { host.messages.say("link_open_failed") },
                say: { text in host.messages.show(text) },
                onClose: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.links")
        .scheduleMessages(host.messages)
        .linkSheet()
        .sheet(item: Binding(get: { host.child }, set: { host.child = $0 })) { item in
            LinkChildSheetView(item: item, next: { route in host.show(route) })
                .environment(router)
        }
    }
}

/// The link editor (`AppRoutes.LinkEditor`, a new link or an own one): the Compose sheet content
/// (`linkEditorViewController`, IO-09f) at full height, since its fields bring the keyboard up. A saved link closes it;
/// a failed save says why over it.
struct LinkEditorSheetView: View {
    let key: AppRoutes.LinkEditor
    @Environment(\.dismiss) private var dismiss
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [key, messages, dismiss] in
            linkEditorViewController(key: key, say: { text in messages.show(text) }, onClose: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.linkEditor")
        .scheduleMessages(messages)
        .linkSheet(detents: [.large])
    }
}

/// One link's actions (`AppRoutes.LinkActions`, a long press on a link): the Compose sheet content
/// (`linkActionsViewController`, IO-09f); the system's drag indicator lies over the handle it draws. The author's
/// profile opens on the tab's stack and closes every sheet; the scores sheet and the editor open in this sheet's
/// place: through `next` over all links, else as the router's sheet. A copied address is confirmed here, since iOS
/// shows no confirmation itself.
struct LinkActionsSheetView: View {
    let key: AppRoutes.LinkActions
    /// Where the next sheet goes when this one shows over all links; `nil` for the router's own sheet.
    var next: ((any AppRoute) -> Void)?
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [key, next, router, messages, dismiss] in
            linkActionsViewController(
                key: key,
                open: { route in
                    if route is AppRoutes.UserProfile {
                        messages.opened(router.open(route))
                    } else if let next {
                        next(route)
                    } else {
                        messages.opened(router.replaceSheet(with: route))
                    }
                },
                copy: { url in
                    UIPasteboard.general.string = url
                    messages.say("links_copied")
                },
                linkFailed: { messages.say("link_open_failed") },
                say: { text in messages.show(text) },
                onClose: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.linkActions")
        .scheduleMessages(messages)
        .linkSheet()
    }
}

/// A sheet over all links: a link's actions, the editor or the scores sheet.
struct LinkChildSheetView: View {
    let item: LinkSheetItem
    let next: (any AppRoute) -> Void

    var body: some View {
        switch item.route {
        case let key as AppRoutes.LinkActions:
            LinkActionsSheetView(key: key, next: next)
        case let key as AppRoutes.LinkEditor:
            LinkEditorSheetView(key: key)
        case let scores as AppRoutes.SheetScores:
            SheetScoresSheetView(scores: scores.args)
        default:
            EmptyView()
        }
    }
}

/// The sheet that all links show over themselves, and their messages.
@MainActor
@Observable
final class LinkSheetsHost {
    var child: LinkSheetItem?
    let messages = ScheduleMessages()

    /// A new sheet over the list, in the place of the one shown there.
    func show(_ route: any AppRoute) {
        child = LinkSheetItem(route: route)
    }
}

/// One presentation of a sheet over all links: a new identity for every opening, as each opening is a new sheet.
struct LinkSheetItem: Identifiable {
    let id = UUID()
    let route: any AppRoute
}

extension AppRouter {
    /// Opens `route` in the place of the shown sheet, as Android's links sheets close themselves before the next one:
    /// SwiftUI dismisses the one and presents the other (`.sheet(item:)`). A refused key leaves no sheet.
    func replaceSheet(with route: any AppRoute) -> RouteOpening {
        sheet = nil
        return open(route)
    }
}

extension View {
    /// A links sheet: half height first and full height on a drag (Android's default sheet height) unless `detents`
    /// say otherwise, with the system's drag indicator, the handle a drag closes the sheet by (Compose content takes
    /// the drags that start on it). The see-through Compose content shows the grouped background its `SheetScaffold`
    /// draws in the iOS style around itself.
    func linkSheet(detents: Set<PresentationDetent> = [.medium, .large]) -> some View {
        presentationDetents(detents)
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(uiColor: .systemGroupedBackground))
    }
}
