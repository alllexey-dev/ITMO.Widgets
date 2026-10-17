import Shared
import SwiftUI
import UIKit

/// The bell's sheet (docs/features/home.md, The bell) over the shared `BellViewModel`, one per opening: the events
/// new at this opening under "new" with a dot and a bold title, the rest under "earlier", or "nothing new" over
/// them; each row opens its event's home (the sheet closes first). Opening reads everything; there is no
/// "read all". The notification settings row opens the system settings of the app until its own page.
struct BellSheet: View {
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var model = ObservableViewModel(BellViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?

    var body: some View {
        ItmoSheet(title: AppStrings.string("today_notifications"), height: .tall) {
            BellContent(
                state: model.state,
                actions: BellContentActions(
                    open: { model.viewModel.open(item: $0) },
                    answer: { item, accept in model.viewModel.answer(item: item, accept: accept) },
                    settings: {
                        dismiss()
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                )
            )
        }
        .overlay(alignment: .bottom) {
            if let message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: .seconds(3))
                        if self.message?.id == message.id { self.message = nil }
                    }
            }
        }
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case let .open(open):
                dismiss()
                router.open(entry: open.route)
            case let .answerFailed(failed):
                let text = AppErrorTextsKt.toUiText(failed.error).resolved
                message = SettingsMessage(text: text)
                AccessibilityNotification.Announcement(text).post()
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("bell.sheet")
    }
}

/// What the bell's rows ask their host to do.
struct BellContentActions {
    var open: (BellItem) -> Void = { _ in }
    var answer: (BellItem, Bool) -> Void = { _, _ in }
    var settings: () -> Void = {}
}

/// The bell's list without the ViewModel.
struct BellContent: View {
    let state: BellUiState
    var actions = BellContentActions()

    var body: some View {
        if state.loading {
            ItmoLoadingView().accessibilityIdentifier("bell.loading")
        } else if !state.fresh.isEmpty {
            ItmoSectionHeader(title: AppStrings.string("bell_new"), count: "\(state.fresh.count)")
            events(state.fresh)
            if !state.earlier.isEmpty {
                ItmoSectionHeader(title: AppStrings.string("bell_earlier"))
                events(state.earlier)
            }
        } else {
            Label {
                Text(verbatim: AppStrings.string("bell_nothing_new"))
            } icon: {
                AppSymbol.checkCircle.image.foregroundStyle(ItmoColor.success)
            }
            .font(.itmo(.bodyMedium, emphasized: true))
            .foregroundStyle(.secondary)
            .padding(.horizontal, ItmoSpacing.related)
            .accessibilityIdentifier("bell.nothingNew")
            if !state.earlier.isEmpty { events(state.earlier) }
            if state.empty { ItmoGroupNote(text: AppStrings.string("bell_empty_text")) }
        }
        ItmoListGroup {
            ItmoRow(title: AppStrings.string("bell_settings"), symbol: .settings, chevron: true, action: actions.settings)
                .accessibilityIdentifier("bell.settings")
        }
        .padding(.top, ItmoSpacing.compact)
    }

    private func events(_ rows: [BellRow]) -> some View {
        ItmoListGroup(inset: 68) {
            ForEach(rows, id: \.item.id) { row in
                BellEventRow(row: row, actions: actions)
            }
        }
    }
}

/// One event: its lead (a mark's change of points, the person of a request, the event's icon in its tone), the title
/// with the time and the new dot, the details, `before` struck through over `after`, the answers of a request.
struct BellEventRow: View {
    let row: BellRow
    var actions = BellContentActions()

    var body: some View {
        let item = row.item
        Group {
            if item.request != nil {
                // The answers are buttons of their own; the rest of the row opens the person.
                content
                    .contentShape(.rect)
                    .onTapGesture { actions.open(item) }
                    .accessibilityElement(children: .contain)
            } else if item.target != nil {
                Button { actions.open(item) } label: { content.contentShape(.rect) }.buttonStyle(.plain)
            } else {
                content.accessibilityElement(children: .combine)
            }
        }
        .accessibilityIdentifier("bell.row.\(item.id)")
        .accessibilityValue(Text(verbatim: row.new ? AppStrings.string("bell_new") : ""))
    }

    private var content: some View {
        let item = row.item
        return HStack(alignment: .top, spacing: ItmoSpacing.content) {
            lead
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(verbatim: item.title.resolved)
                        .font(.itmo(.bodyLarge, emphasized: row.new))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if let time = row.time {
                        Text(verbatim: time.resolved).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
                    }
                    if row.new {
                        Circle().fill(ItmoColor.primary).frame(width: 8, height: 8).accessibilityHidden(true)
                    }
                }
                if let text = item.text {
                    Text(verbatim: text.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
                if let was = item.was {
                    Text(verbatim: was.resolved).font(.itmo(.bodyMedium)).strikethrough().foregroundStyle(.secondary)
                }
                if let now = item.now {
                    Text(verbatim: now.resolved).font(.itmo(.bodyMedium, emphasized: true))
                        .foregroundStyle(ItmoTone.warn.color)
                }
                if item.request != nil {
                    HStack(spacing: ItmoSpacing.compact) {
                        ItmoProminentButton(title: AppStrings.string("bell_accept")) { actions.answer(item, true) }
                            .accessibilityIdentifier("bell.accept.\(item.id)")
                        Button { actions.answer(item, false) } label: { Text(verbatim: AppStrings.string("bell_decline")) }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("bell.decline.\(item.id)")
                    }
                    .controlSize(.small)
                    .disabled(row.busy)
                    .padding(.top, 6)
                }
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
    }

    @ViewBuilder
    private var lead: some View {
        let item = row.item
        switch item.kind {
        case .mark:
            if let lead = item.lead {
                Text(verbatim: lead)
                    .font(.itmo(.bodyMedium, emphasized: true))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .foregroundStyle(ItmoTone.ok.color)
                    .frame(width: 40, height: 40)
                    .background(ItmoTone.ok.wash, in: .rect(cornerRadius: 10))
                    .accessibilityHidden(true)
            } else {
                ItmoIconTile(symbol: .menuBook, tone: .ok)
            }
        case .scheduleChange:
            ItmoIconTile(symbol: .editCalendar, tone: .warn)
        case .sport:
            ItmoIconTile(symbol: .sprint, tone: item.failed ? .bad : .ok)
        case .friendRequest:
            ItmoAvatar(initials: Self.initials(item.lead ?? ""), pictureUrl: item.pictureUrl)
        }
    }

    /// A two-word name -> its two initials.
    static func initials(_ name: String) -> String {
        name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }
}
