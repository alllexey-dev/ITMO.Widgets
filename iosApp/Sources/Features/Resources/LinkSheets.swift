import Shared
import SwiftUI
import UIKit

/// All links of a subject period (`AppRoutes.SubjectLinks`, from the subject page) over the shared
/// `SubjectLinksViewModel`: the subject under the title, then a heading and a group per category, chats and the links of
/// past years; `+` adds a link. A tap opens a link, a long press its actions; a link's actions and the editor show over
/// the list, as Android stacks them over its sheet; there each replaces the other, and the scores sheet replaces them,
/// since a links sheet that leads on closes itself first. A failed vote or refresh says why over the list.
struct SubjectLinksSheetView: View {
    let args: SubjectLinksArgs
    @Environment(AppRouter.self) private var router
    @Environment(\.openURL) private var openURL
    @State private var model: ObservableViewModel<SubjectLinksViewModel, SubjectLinksUiState>
    @State private var host = LinkSheetsHost()

    init(args: SubjectLinksArgs) {
        self.args = args
        _model = State(initialValue: .links(args: args, linkId: nil))
    }

    var body: some View {
        let viewModel = model.viewModel
        ItmoSheet(title: AppStrings.string("links_title"), height: .tall) {
            SubjectLinksContent(
                subject: args.subjectName,
                state: model.state,
                actions: SubjectLinksContentActions(
                    open: { link in openURL.link(link.url) { host.messages.say("link_open_failed") } },
                    linkActions: { host.show(AppRoutes.LinkActions(args: args, linkId: $0.id)) },
                    vote: { link, up in viewModel.vote(id: link.id, up: up) },
                    retry: { viewModel.refresh(mode: .pull) }
                )
            )
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { host.show(AppRoutes.LinkEditor(args: args, linkId: nil)) } label: { AppSymbol.add.image }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("links_add")))
                        .accessibilityIdentifier("subject_links_add")
                }
            }
        }
        .observing(model)
        .onEvents(of: model, \.events) { event in
            if let failed = event as? LinkEventFailed { host.messages.show(AppErrorTextsKt.toUiText(failed.error).resolved) }
        }
        .scheduleMessages(host.messages)
        .sheet(item: Binding(get: { host.child }, set: { host.child = $0 })) { item in
            LinkChildSheetView(item: item, next: { route in host.show(route) })
                .environment(router)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.links")
    }
}

/// What the rows of all links ask; snapshot tests pass no-ops.
struct SubjectLinksContentActions {
    var open: (SubjectLink) -> Void = { _ in }
    var linkActions: (SubjectLink) -> Void = { _ in }
    var vote: (SubjectLink, Bool) -> Void = { _, _ in }
    var retry: () -> Void = {}
}

/// All links without the ViewModel: the subject, then loading, the error with a retry, the empty state or the
/// sections. Own links say `links_mine`, others carry the vote pill, without arrows when voting is restricted or the app has
/// no connection.
struct SubjectLinksContent: View {
    let subject: String
    let state: SubjectLinksUiState
    var actions = SubjectLinksContentActions()

    var body: some View {
        Text(verbatim: subject).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
        if let snapshot = state.content {
            if state.sections.isEmpty {
                ItmoEmptyView(symbol: .link, title: AppStrings.string("links_empty"))
                    .accessibilityIdentifier("subject_links_state")
            }
            ForEach(Array(state.sections.enumerated()), id: \.offset) { _, section in
                ItmoSectionHeader(title: title(of: section))
                ItmoListGroup {
                    ForEach(section.links, id: \.id) { link in
                        LinkRowView(
                            link: link,
                            caption: linkMeta(link: link, pinned: link.id == snapshot.pinnedId, previous: section is LinkSectionPrevious)
                                .resolved,
                            canVote: state.canVote,
                            actions: actions
                        )
                    }
                }
            }
        } else if let error = state.error {
            ItmoErrorView(
                title: AppStrings.string("links_error_title"),
                description: AppErrorTextsKt.toUiText(error).resolved,
                retry: actions.retry
            )
            .accessibilityIdentifier("subject_links_state")
        } else {
            ItmoLoadingView().frame(minHeight: 256).accessibilityIdentifier("subject_links_loading")
        }
    }

    /// The category's name, `links_chats` for chats, `links_previous` for past links.
    private func title(of section: LinkSection) -> String {
        guard let category = (section as? LinkSectionCategory)?.category else {
            return AppStrings.string("links_previous")
        }
        return category == .chat ? AppStrings.string("links_chats") : category.title().resolved
    }
}

/// A link: its category's tile, the title over the caption, `links_mine` on an own link, the votes of another's. A tap opens
/// it, a long press its actions.
private struct LinkRowView: View {
    let link: SubjectLink
    let caption: String
    let canVote: Bool
    let actions: SubjectLinksContentActions

    var body: some View {
        HStack(spacing: ItmoSpacing.content) {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(
                    symbol: linkIcon(category: link.category, url: link.url).symbol,
                    tone: link.category == .chat ? .primary : .neutral,
                    size: 30
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: link.displayTitle()).font(.itmo(.bodyLarge)).foregroundStyle(Color.primary).lineLimit(2)
                    if !caption.isEmpty {
                        Text(verbatim: caption).font(.itmo(.bodyMedium)).foregroundStyle(.secondary).lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
            }
            .contentShape(.rect)
            // A long press opens the actions without also opening the link.
            .onTapGesture { actions.open(link) }
            .onLongPressGesture { actions.linkActions(link) }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { actions.open(link) }
            .accessibilityAction(named: Text(verbatim: AppStrings.string("links_actions"))) { actions.linkActions(link) }
            if link.isMine {
                ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("links_mine")))
            } else {
                ItmoVotePill(
                    score: Int(link.score),
                    myVote: Int(link.myVote),
                    upLabel: AppStrings.string("links_vote_up"),
                    downLabel: AppStrings.string("links_vote_down"),
                    enabled: canVote,
                    onUp: { actions.vote(link, true) },
                    onDown: { actions.vote(link, false) }
                )
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
        .accessibilityIdentifier("subject_links.link.\(link.id)")
    }
}

/// One link's actions (`AppRoutes.LinkActions`, a long press on a link) over its own `SubjectLinksViewModel`: the link
/// with its votes, the moderator's reason under an own rejected or hidden link, then the rows the shared rules allow
/// (`linkActions`). One action runs at a time; every action but a vote closes the sheet once it succeeds, a failure
/// says why over it, and a link deleted here or elsewhere closes it. The author's profile opens on the tab's stack and
/// closes every sheet; the scores sheet and the editor open in this sheet's place: through `next` over all links, else
/// as the router's sheet. A copied address is confirmed here, since iOS shows no confirmation itself; the report is a
/// form sheet over this one, and an accepted report closes both.
struct LinkActionsSheetView: View {
    let key: AppRoutes.LinkActions
    /// Where the next sheet goes when this one shows over all links; `nil` for the router's own sheet.
    var next: ((any AppRoute) -> Void)?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(AppRouter.self) private var router
    @State private var model: ObservableViewModel<SubjectLinksViewModel, SubjectLinksUiState>
    @State private var messages = ScheduleMessages()
    @State private var confirmingDelete = false
    @State private var reporting = false

    init(key: AppRoutes.LinkActions, next: ((any AppRoute) -> Void)? = nil) {
        self.key = key
        self.next = next
        _model = State(initialValue: .links(args: key.args, linkId: key.linkId))
    }

    var body: some View {
        let state = model.state
        let shown = state.linkActions(linkId: key.linkId)
        ItmoSheet(title: AppStrings.string("links_actions")) {
            if let shown {
                LinkActionsContent(model: shown, run: run, vote: vote)
            }
        }
        .observing(model)
        .onEvents(of: model, \.events) { event in
            if let failed = event as? LinkEventFailed {
                messages.show(AppErrorTextsKt.toUiText(failed.error).resolved)
            } else {
                dismiss()
            }
        }
        // A link deleted here or elsewhere closes the sheet unless an action is pending.
        .onChange(of: state.content != nil && shown == nil && !state.busy) { _, gone in
            if gone { dismiss() }
        }
        .alert(Text(verbatim: AppStrings.string("links_delete_confirm")), isPresented: $confirmingDelete) {
            Button(role: .destructive) { model.viewModel.delete(id: key.linkId) } label: {
                Text(verbatim: AppStrings.string("links_delete"))
            }
            Button(role: .cancel) {} label: { Text(verbatim: AppStrings.string("common_cancel")) }
        }
        .sheet(isPresented: $reporting) {
            ReportLinkSheet(args: key.args, linkId: key.linkId) { dismiss() }
        }
        .scheduleMessages(messages)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.linkActions")
    }

    /// No action while another is in flight.
    private var idle: Bool {
        !model.viewModel.uiState.value.busy
    }

    private func vote(_ link: SubjectLink, up: Bool) {
        if idle { model.viewModel.vote(id: link.id, up: up) }
    }

    /// The rows that act wait while an action is in flight; open, copy and the author's profile stay.
    private func run(_ action: LinkAction, _ link: SubjectLink) {
        let args = key.args
        switch action {
        case .open:
            openURL.link(link.url, opened: { dismiss() }) { messages.say("link_open_failed") }
        case .author:
            guard let isu = link.author?.isu, isu > 0 else { return }
            messages.opened(router.open(AppRoutes.UserProfile(isu: isu)))
        case .theCopy:
            UIPasteboard.general.string = link.url
            messages.say("links_copied")
        case .scores:
            guard idle else { return }
            let scores = SheetScoresArgs(
                subjectId: args.subjectId, subjectName: args.subjectName, periodKey: args.periodKey, url: link.url,
                step: .connect
            )
            lead(to: AppRoutes.SheetScores(args: scores))
        case .pin:
            if idle { model.viewModel.pin(id: link.id) }
        case .edit:
            if idle { lead(to: AppRoutes.LinkEditor(args: args, linkId: link.id)) }
        case .delete:
            if idle { confirmingDelete = true }
        case .report:
            if idle { reporting = true }
        }
    }

    /// The next sheet in this one's place.
    private func lead(to route: any AppRoute) {
        if let next {
            next(route)
        } else {
            messages.opened(router.replaceSheet(with: route))
        }
    }
}

/// One link's actions without the ViewModel: the link card with its votes and review note, then the rows.
struct LinkActionsContent: View {
    let model: LinkActionsModel
    var run: (LinkAction, SubjectLink) -> Void = { _, _ in }
    var vote: (SubjectLink, Bool) -> Void = { _, _ in }

    var body: some View {
        let link = model.link
        ItmoCard {
            HStack(spacing: ItmoSpacing.content) {
                VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                    Text(verbatim: link.displayTitle())
                        .font(.itmo(.titleMedium, emphasized: true))
                        .accessibilityAddTraits(.isHeader)
                    Text(verbatim: linkMeta(link: link, pinned: model.pinned, previous: model.previous).resolved)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if model.showsScore {
                    ItmoVotePill(
                        score: Int(link.score),
                        myVote: Int(link.myVote),
                        upLabel: AppStrings.string("links_vote_up"),
                        downLabel: AppStrings.string("links_vote_down"),
                        enabled: model.canVote,
                        onUp: { vote(link, true) },
                        onDown: { vote(link, false) }
                    )
                }
            }
            if let note = model.reviewNote {
                Text(verbatim: AppStrings.string("links_rejected_reason", [note]))
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(ItmoColor.error)
                    .accessibilityIdentifier("link_actions_note")
            }
        }
        ItmoListGroup {
            ForEach(model.rows, id: \.self) { action in
                let row = Self.row(action, model)
                ItmoRow(title: row.title, symbol: row.symbol, danger: action == .delete) { run(action, link) }
                    .accessibilityIdentifier("link_actions_\(action.name.lowercased())")
            }
        }
    }

    private static func row(_ action: LinkAction, _ model: LinkActionsModel) -> (title: String, symbol: AppSymbol) {
        switch action {
        case .open: (AppStrings.string("links_open"), .openInNew)
        case .author: (AppStrings.string("links_author", [model.author ?? ""]), .person)
        case .theCopy: (AppStrings.string("links_copy"), .contentCopy)
        case .scores: (AppStrings.string("sheet_scores_action"), .table)
        case .pin: model.pinned ? (AppStrings.string("links_unpin"), .keepOff) : (AppStrings.string("links_pin"), .keep)
        case .edit: (AppStrings.string("links_edit"), .edit)
        case .delete: (AppStrings.string("links_delete"), .delete)
        case .report: (AppStrings.string("links_report"), .flag)
        }
    }
}

/// The report of a link (`links_report_title`) over its actions, with its own `SubjectLinksViewModel` so the sheet under
/// it does not take its answer: the four reasons and an optional comment; while it sends the sheet stays, a failure
/// shows under the comment until the next send, an accepted report closes it and `done` closes the actions too.
struct ReportLinkSheet: View {
    let linkId: String
    let done: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<SubjectLinksViewModel, SubjectLinksUiState>
    @State private var reason: ResourceReportReason?
    @State private var comment = ""
    @State private var failure: String?

    init(args: SubjectLinksArgs, linkId: String, done: @escaping () -> Void) {
        self.linkId = linkId
        self.done = done
        _model = State(initialValue: .links(args: args, linkId: linkId))
    }

    var body: some View {
        let sending = model.state.busy
        NavigationStack {
            ReportLinkForm(reason: $reason, comment: $comment, failure: failure, sending: sending)
                .navigationTitle(Text(verbatim: AppStrings.string("links_report_title")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(role: .cancel) { dismiss() } label: { Text(verbatim: AppStrings.string("common_cancel")) }
                            .disabled(sending)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(action: send) {
                            if sending { ProgressView() } else { Text(verbatim: AppStrings.string("links_report_send")) }
                        }
                        .disabled(reason == nil || sending)
                        .accessibilityLabel(Text(verbatim: AppStrings.string("links_report_send")))
                        .accessibilityIdentifier("links_report_send")
                    }
                }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled(sending)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            if let failed = event as? LinkEventFailed {
                failure = AppErrorTextsKt.toUiText(failed.error).resolved
            } else {
                dismiss()
                done()
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.linkReport")
    }

    private func send() {
        guard let reason else { return }
        failure = nil
        let trimmed = comment.trimmingCharacters(in: .whitespacesAndNewlines)
        model.viewModel.report(id: linkId, reason: reason, comment: trimmed.isEmpty ? nil : trimmed)
    }
}

/// The reasons and the comment of a link's report without the ViewModel.
struct ReportLinkForm: View {
    @Binding var reason: ResourceReportReason?
    @Binding var comment: String
    var failure: String?
    var sending = false

    var body: some View {
        ItmoReportForm(
            reasons: ResourceReportReason.allCases,
            label: { AppStrings.string("links_report_\($0.name.lowercased())") },
            commentLabel: AppStrings.string("links_report_comment"),
            reason: $reason,
            comment: $comment,
            failure: failure,
            sending: sending
        )
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

extension ObservableViewModel where Model == SubjectLinksViewModel, State == SubjectLinksUiState {
    /// The links of one subject period; `linkId` names the link an actions sheet or a report acts on.
    static func links(args: SubjectLinksArgs, linkId: String?) -> ObservableViewModel {
        ObservableViewModel(state: \.uiState) { store in
            let parameters = subjectLinksParameters(args: args, linkId: linkId)
            guard let model = store.resolve(type: SubjectLinksViewModel.self, parameters: parameters)
                as? SubjectLinksViewModel else {
                preconditionFailure("Koin resolved no SubjectLinksViewModel")
            }
            return model
        }
    }
}

extension OpenURLAction {
    /// Opens a link's `url` in the app that takes it; `failed` when none does or the address is no URL.
    func link(_ url: String, opened: @escaping () -> Void = {}, failed: @escaping () -> Void) {
        guard let address = URL(string: url) else { return failed() }
        self(address) { accepted in accepted ? opened() : failed() }
    }
}
