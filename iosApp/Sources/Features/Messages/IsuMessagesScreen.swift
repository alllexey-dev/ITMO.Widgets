import QuickLook
import Shared
import SwiftUI

/// `isu_messages_title` (`AppRoutes.IsuMessages`, docs/features/isu-messages.md) over the shared `IsuMessagesViewModel`:
/// the inbox and the archive, `isu_messages_more`, one message pushed over the list with its attachments in Quick Look. The body
/// is the shared view model's plain blocks, never HTML; links open in Safari.
struct IsuMessagesScreen: View {
    @State private var model = ObservableViewModel(IsuMessagesViewModel.self, state: \.uiState)
    @State private var file: URL?
    @State private var message: SettingsMessage?
    /// The last opened message, so the pushed screen keeps its content while it slides away.
    @State private var shown: IsuMessageDetail?

    var body: some View {
        IsuMessagesContent(state: model.state, actions: actions)
            .refreshable {
                await model.pullRefresh({ $0.refresh(mode: .pull) }, while: { $0.uiState.value.refreshing })
            }
            .itmoDetailHeader(title: AppStrings.string("isu_messages_title"), subtitle: model.state.unreadText?.resolved)
            .navigationDestination(isPresented: opened) {
                if let detail = model.state.opened ?? shown {
                    IsuMessageDetailView(detail: detail, downloading: model.state.downloading, actions: actions)
                        .quickLookPreview($file)
                        .overlay(alignment: .bottom) { banner }
                }
            }
            .onChange(of: model.state.opened) { _, opened in
                if let opened { shown = opened }
            }
            .observing(model)
            .onEvents(of: model, \.events) { event in
                switch onEnum(of: event) {
                case let .openFile(open):
                    file = URL(string: open.uri)
                case let .snackbar(snackbar):
                    message = SettingsMessage(text: snackbar.text.resolved)
                }
            }
            .overlay(alignment: .bottom) { banner }
            .animation(.default, value: message?.id)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("isu_messages.screen")
    }

    private var actions: IsuMessagesActions {
        IsuMessagesActions(
            folder: { model.viewModel.selectFolder(folder: $0) },
            retry: { model.viewModel.refresh(mode: .force) },
            more: { model.viewModel.loadMore() },
            open: { model.viewModel.open(id: $0) },
            attachment: { model.viewModel.openAttachment(id: $0) }
        )
    }

    private var opened: Binding<Bool> {
        Binding(get: { model.state.opened != nil }, set: { if !$0 { model.viewModel.close() } })
    }

    @ViewBuilder private var banner: some View {
        if let message {
            SettingsMessageBanner(text: message.text)
                .task(id: message.id) {
                    try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                    if self.message?.id == message.id { self.message = nil }
                }
        }
    }
}

/// What the screen's controls do; snapshot tests pass no-ops.
struct IsuMessagesActions {
    var folder: (IsuFolder) -> Void = { _ in }
    var retry: () -> Void = {}
    var more: () -> Void = {}
    var open: (Int64) -> Void = { _ in }
    var attachment: (String) -> Void = { _ in }
}

/// The list for one `IsuMessagesUiState`, without the ViewModel.
struct IsuMessagesContent: View {
    let state: IsuMessagesUiState
    var actions = IsuMessagesActions()

    var body: some View {
        ItmoScreenStack {
            ItmoSegmentedChoice(
                label: AppStrings.string("isu_messages_title"),
                options: [IsuFolder.inbox, IsuFolder.archive],
                selection: Binding(get: { state.folder }, set: actions.folder),
                title: { AppStrings.string($0 == .inbox ? "isu_messages_inbox" : "isu_messages_archive") }
            )
            .padding(.top, ItmoSpacing.compact)
            list
        }
    }

    @ViewBuilder private var list: some View {
        switch onEnum(of: state.list) {
        case .loading:
            ItmoLoadingView()
        case let .empty(empty):
            ItmoEmptyView(symbol: .mail, title: empty.title.resolved, description: empty.text.resolved)
        case let .failed(failed):
            ItmoErrorView(title: failed.title.resolved, description: failed.text.resolved, retry: actions.retry)
                .accessibilityIdentifier("isu_messages.error")
        case let .messages(messages):
            ItmoListGroup {
                ForEach(messages.rows, id: \.id) { row in
                    IsuMessageRowView(row: row) { actions.open(row.id) }
                }
            }
            more(messages.more)
        }
    }

    @ViewBuilder private func more(_ more: MoreState) -> some View {
        switch more {
        case .end:
            EmptyView()
        case .loading:
            ProgressView().frame(maxWidth: .infinity).padding(.vertical, ItmoSpacing.content)
        case .available, .failed:
            Button(AppStrings.string(more == .failed ? "isu_messages_more_failed" : "isu_messages_more"), action: actions.more)
                .buttonStyle(.bordered)
                .itmoTint()
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("isu_messages.more")
        }
    }
}

/// Sender, paperclip and date on the first line, the subject, two lines of the text; a dot marks an unread one.
struct IsuMessageRowView: View {
    let row: IsuMessageRow
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: ItmoSpacing.content) {
                ItmoAvatar(initials: UserNamesKt.personInitials(name: row.sender), size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: ItmoSpacing.related) {
                        Text(verbatim: row.sender)
                            .font(.itmo(.titleSmall, emphasized: row.unread))
                            .lineLimit(1)
                        Spacer(minLength: 0)
                        if row.attachments > 0 {
                            AppSymbol.attachFile.image.font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                        }
                        Text(verbatim: row.date)
                            .font(.itmo(.labelMedium))
                            .foregroundStyle(row.unread ? ItmoColor.primary : Color.secondary)
                        if row.unread {
                            Circle().fill(ItmoColor.primary).frame(width: 8, height: 8)
                        }
                    }
                    Text(verbatim: row.subject)
                        .font(.itmo(.bodyMedium, emphasized: row.unread))
                        .lineLimit(1)
                    Text(verbatim: row.preview)
                        .font(.itmo(.bodySmall))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                .multilineTextAlignment(.leading)
            }
            .padding(.horizontal, ItmoKit.padding)
            .padding(.vertical, ItmoSpacing.content)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityIdentifier("isu_messages.row.\(row.id)")
    }

    private var label: String {
        var parts = [String]()
        if row.unread { parts.append(AppStrings.string("isu_messages_unread")) }
        parts += [row.sender, row.subject, row.date]
        if row.attachments > 0 { parts.append(AppStrings.string("isu_messages_attachments_accessibility", [row.attachments])) }
        parts.append(row.preview)
        return parts.joined(separator: ". ")
    }
}

/// One message: the subject as the header, sender and date, the body as selectable styled text, the attachments.
struct IsuMessageDetailView: View {
    let detail: IsuMessageDetail
    var downloading: String?
    var actions = IsuMessagesActions()

    var body: some View {
        ItmoScreenStack {
            HStack(spacing: ItmoSpacing.content) {
                ItmoAvatar(initials: UserNamesKt.personInitials(name: detail.sender), size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: detail.sender).font(.itmo(.titleSmall, emphasized: true))
                    Text(verbatim: detail.date).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                }
            }
            .padding(.top, ItmoSpacing.compact)
            VStack(alignment: .leading, spacing: ItmoSpacing.content) {
                ForEach(Array(detail.body.blocks.enumerated()), id: \.offset) { _, block in
                    HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.related) {
                        if let marker = block.marker {
                            Text(verbatim: marker).foregroundStyle(.secondary).frame(minWidth: 20, alignment: .leading)
                        }
                        Text(Self.attributed(block))
                    }
                    .font(.itmo(.bodyLarge))
                }
            }
            .textSelection(.enabled)
            .itmoTint()
            if !detail.attachments.isEmpty {
                ItmoSectionHeader(title: AppStrings.string("isu_messages_files"), count: "\(detail.attachments.count)")
                ItmoListGroup {
                    ForEach(detail.attachments, id: \.id) { file in
                        ItmoRow(
                            title: file.name,
                            subtitle: file.kind.resolved,
                            symbol: .attachFile,
                            tone: .neutral,
                            action: { actions.attachment(file.id) }
                        )
                        .accessory {
                            if downloading == file.id { ProgressView() }
                        }
                        .accessibilityIdentifier("isu_messages.attachment")
                    }
                }
            }
            if detail.unread {
                ItmoGroupNote(text: AppStrings.string("isu_messages_unread_note"))
            }
        }
        .itmoDetailHeader(title: detail.subject, subtitle: nil)
        .accessibilityIdentifier("isu_messages.detail")
    }

    /// The block's runs with their styles; a link is an `https` address the shared parser let through.
    static func attributed(_ block: MessageBlock) -> AttributedString {
        block.spans.reduce(into: AttributedString()) { result, span in
            var part = AttributedString(span.text)
            var intent: InlinePresentationIntent = []
            if span.bold { intent.insert(.stronglyEmphasized) }
            if span.italic { intent.insert(.emphasized) }
            part.inlinePresentationIntent = intent
            if span.underline { part.underlineStyle = .single }
            if let link = span.link, let url = URL(string: link) { part.link = url }
            result += part
        }
    }
}
