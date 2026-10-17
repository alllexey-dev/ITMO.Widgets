import Shared
import SwiftUI

/// The scores sheet (`AppRoutes.SheetScores`, `sheet_scores_title`, docs/features/sheet-scores.md) over the shared
/// `SheetScoresViewModel`: the own row among several, the tab to look in, a row of it, or the total's review. It
/// closes itself once a total is saved; a failed save says why over it.
struct SheetScoresSheetView: View {
    let scores: SheetScoresArgs
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<SheetScoresViewModel, SheetScoresUiState>
    @State private var messages = ScheduleMessages()

    init(scores: SheetScoresArgs) {
        self.scores = scores
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = SheetScoresIosParameters.shared.viewModel(args: scores)
            guard let model = store.resolve(type: SheetScoresViewModel.self, parameters: parameters)
                as? SheetScoresViewModel else {
                preconditionFailure("Koin resolved no SheetScoresViewModel")
            }
            return model
        })
    }

    var body: some View {
        ItmoSheet(title: AppStrings.string("sheet_scores_title"), height: .tall) {
            SheetScoresContent(subject: scores.subjectName, state: model.state, actions: actions)
        }
        .observing(model)
        .onChange(of: model.state is SheetScoresUiStateDone, initial: true) { _, done in
            if done { dismiss() }
        }
        .onEvents(of: model, \.events) { event in
            if let failed = event as? SheetScoresEventSaveFailed { messages.show(failed.text.resolved) }
        }
        .scheduleMessages(messages)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.sheetScores")
    }

    private var actions: SheetScoresContentActions {
        let viewModel = model.viewModel
        return SheetScoresContentActions(
            retry: { viewModel.refresh(mode: .force) },
            pickRow: { viewModel.pickRow(match: $0) },
            pickTab: { viewModel.pickTab(tab: $0) },
            pickTotal: { viewModel.pickTotal(cell: $0) },
            resetTotal: { viewModel.resetTotal() }
        )
    }
}

/// What the sheet's rows, chips and cells ask the ViewModel; snapshot tests pass no-ops.
struct SheetScoresContentActions {
    var retry: () -> Void = {}
    var pickRow: (SheetRowMatch) -> Void = { _ in }
    var pickTab: (SheetTab) -> Void = { _ in }
    var pickTotal: (SheetCell) -> Void = { _ in }
    var resetTotal: () -> Void = {}
}

/// The sheet without the ViewModel: the subject, then loading, a failure (a retry only without a connection), the
/// rows or tabs to choose from, or the total's review. Stateless but for the name search and the open grid.
struct SheetScoresContent: View {
    let subject: String
    let state: SheetScoresUiState
    var actions = SheetScoresContentActions()

    var body: some View {
        Text(verbatim: subject).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
        switch onEnum(of: state) {
        case .loading:
            ItmoLoadingView().frame(minHeight: 288)
        case let .failed(failed):
            let title = failed.status.text()?.resolved ?? AppStrings.string("sheet_scores_offline")
            if failed.status == .network {
                ItmoErrorView(title: title, retry: actions.retry)
            } else {
                ItmoEmptyView(symbol: .error, title: title)
            }
        case let .pickRow(pick):
            SheetRowChoice(rows: pick.candidates, pick: actions.pickRow)
        case let .pickTabRow(pick):
            SheetRowChoice(rows: pick.rows, pick: actions.pickRow)
        case let .pickTab(pick):
            ItmoSectionHeader(title: AppStrings.string("sheet_scores_pick_tab"))
            ItmoListGroup(inset: 16) {
                ForEach(pick.tabs, id: \.gid) { tab in
                    ItmoRow(
                        title: tab.name.isEmpty ? AppStrings.string("sheet_scores_pick_tab") : tab.name,
                        chevron: true
                    ) { actions.pickTab(tab) }
                }
            }
        case let .pickTotal(review):
            SheetTotalReview(state: review, actions: actions)
        case .done:
            EmptyView()
        }
    }
}

/// The people to choose from, each with its tab; more than eight get the name search.
private struct SheetRowChoice: View {
    let rows: [SheetRowMatch]
    let pick: (SheetRowMatch) -> Void
    @State private var query = ""

    var body: some View {
        ItmoSectionHeader(title: AppStrings.string("sheet_scores_pick_row"))
        if rows.count > 8 {
            ItmoSearchField(
                placeholder: AppStrings.string("sheet_scores_search_hint"),
                query: $query,
                clearLabel: AppStrings.string("sheet_scores_search_clear")
            )
        }
        let needle = SheetText.shared.normalize(text: query)
        let shown = needle.isEmpty ? rows : rows.filter { SheetText.shared.normalize(text: $0.label).contains(needle) }
        if shown.isEmpty {
            ItmoGroupNote(text: AppStrings.string("sheet_scores_search_empty"))
        } else {
            ItmoListGroup(inset: 16) {
                ForEach(shown.indices, id: \.self) { index in
                    let row = shown[index]
                    ItmoRow(title: row.label, subtitle: row.tab.name.isEmpty ? nil : row.tab.name) { pick(row) }
                }
            }
        }
    }
}

/// The total's review: the total, its column and tab and why it is the total (a reason to check in the warning
/// colour), the own row around it, the candidates and `sheet_scores_other_cell` as chips, `sheet_scores_reset_auto` for a
/// hand-picked total. The other-cell chip, or a row without a total header, opens every cell of the row by tab. Any
/// tap saves.
private struct SheetTotalReview: View {
    let state: SheetScoresUiStatePickTotal
    let actions: SheetScoresContentActions
    @State private var grid = false

    var body: some View {
        ItmoSectionHeader(title: AppStrings.string("sheet_scores_pick_total"))
        if let shown = state.shown {
            ItmoCard(tone: state.unsure ? .warn : .primary) {
                Text(verbatim: shown.value.isEmpty ? AppStrings.string("sheet_scores_empty_cell") : shown.value)
                    .font(.itmo(.headlineMedium, emphasized: true))
                    .monospacedDigit()
                Text(verbatim: caption(shown)).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                if let note = state.note {
                    Text(verbatim: note.resolved)
                        .font(.itmo(.bodySmall))
                        .foregroundStyle(state.unsure ? ItmoColor.warning : .secondary)
                }
            }
            .accessibilityElement(children: .combine)
            SheetRowPreview(cells: state.preview, shown: shown, pick: actions.pickTotal)
            let candidates = state.candidates
            ItmoChips(
                chips: candidates.map { ItmoChip(label: state.chip(cell: $0).resolved, selected: $0 == shown) } +
                    [ItmoChip(label: AppStrings.string("sheet_scores_other_cell"), selected: grid)],
                wrap: true
            ) { index in
                if index < candidates.count { actions.pickTotal(candidates[index]) } else { grid.toggle() }
            }
            if state.canReset {
                Button(AppStrings.string("sheet_scores_reset_auto"), action: actions.resetTotal)
                    .buttonStyle(.bordered)
                    .itmoTint()
                    .accessibilityIdentifier("sheetScores.reset")
            }
        }
        if grid || state.shown == nil {
            if state.cells.allSatisfy({ $0.value.isEmpty }) {
                ItmoGroupNote(text: AppStrings.string("sheet_scores_no_values"))
            }
            ForEach(tabs, id: \.gid) { tab in
                if !tab.name.isEmpty { ItmoSectionHeader(title: tab.name) }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: ItmoSpacing.compact)], spacing: ItmoSpacing.compact) {
                    ForEach(state.cells.filter { $0.tab == tab }, id: \.key) { cell in
                        SheetCellTile(cell: cell, selected: cell == state.shown, pick: actions.pickTotal)
                    }
                }
            }
        }
    }

    private var tabs: [SheetTab] {
        state.cells.reduce(into: [SheetTab]()) { tabs, cell in if !tabs.contains(cell.tab) { tabs.append(cell.tab) } }
    }

    private func caption(_ cell: SheetCell) -> String {
        let path = SheetScoresUiStateKt.title(cell: cell).resolved.replacingOccurrences(of: " · ", with: " › ")
        return cell.tab.name.isEmpty ? path : AppStrings.string("sheet_scores_caption", [path, cell.tab.name])
    }
}

/// The own row around the total with the headers, scrolled so that the total shows.
private struct SheetRowPreview: View {
    let cells: [SheetCell]
    let shown: SheetCell
    let pick: (SheetCell) -> Void
    @State private var position: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: ItmoSpacing.compact) {
                ForEach(cells, id: \.key) { cell in
                    SheetCellTile(cell: cell, selected: cell == shown, pick: pick).frame(width: 112)
                }
            }
            .scrollTargetLayout()
            .padding(.horizontal, ItmoKit.margin)
        }
        .scrollPosition(id: $position, anchor: .center)
        .padding(.horizontal, -ItmoKit.margin)
        .onAppear { position = shown.key }
    }
}

/// One cell: its header over its value, the whole tile the target; the total tinted and selected.
private struct SheetCellTile: View {
    let cell: SheetCell
    let selected: Bool
    let pick: (SheetCell) -> Void

    var body: some View {
        Button { pick(cell) } label: {
            VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                Text(verbatim: SheetScoresUiStateKt.title(cell: cell).resolved)
                    .font(.itmo(.labelSmall))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.leading)
                Text(verbatim: cell.value.isEmpty ? AppStrings.string("sheet_scores_empty_cell") : cell.value)
                    .font(.itmo(.titleSmall, emphasized: true))
                    .foregroundStyle(selected ? ItmoColor.primary : Color.primary)
            }
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .topLeading)
            .padding(ItmoSpacing.compact)
            .background(
                selected ? ItmoColor.primary.opacity(0.14) : Color(.tertiarySystemFill),
                in: .rect(cornerRadius: 12)
            )
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private extension SheetCell {
    /// The same cell across readings: its tab and column.
    var key: String { "\(tab.gid):\(column)" }
}
