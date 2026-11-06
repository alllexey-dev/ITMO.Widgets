import Shared
import SwiftUI

/// The scores sheet (`AppRoutes.SheetScores`, `sheet_scores_title`, docs/features/sheet-scores.md) over the shared
/// `SheetScoresViewModel`: one question per step, the own row among several, the tab to look in and its rows, then
/// the total with its variants and ways, the column list and the sum. It closes itself once a total is saved or
/// nothing changes; a failed save says why over it.
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
            select: { viewModel.select(option: $0) },
            openColumns: { viewModel.openColumns() },
            openSum: { viewModel.openSum() },
            pickColumn: { viewModel.pickColumn(cell: $0) },
            toggleSum: { viewModel.toggleSum(cell: $0) },
            finishSum: { viewModel.finishSum() },
            analyze: { viewModel.analyze() },
            save: { viewModel.save() },
            back: { viewModel.back() }
        )
    }
}

/// What the sheet's rows and buttons ask the ViewModel; snapshot tests pass no-ops.
struct SheetScoresContentActions {
    var retry: () -> Void = {}
    var pickRow: (SheetRowMatch) -> Void = { _ in }
    var pickTab: (SheetTab) -> Void = { _ in }
    var select: (TotalOption) -> Void = { _ in }
    var openColumns: () -> Void = {}
    var openSum: () -> Void = {}
    var pickColumn: (SheetCell) -> Void = { _ in }
    var toggleSum: (SheetCell) -> Void = { _ in }
    var finishSum: () -> Void = {}
    var analyze: () -> Void = {}
    var save: () -> Void = {}
    var back: () -> Void = {}
}

/// The sheet without the ViewModel: the subject, then loading, a failure (a retry only without a connection), the
/// rows or tabs to choose from, or the total's pages. Stateless but for the name search, the tab of a column page and
/// the open folds. A step inside the sheet (a tab's rows, a column page) gets `common_back` in the bar; a column page
/// names itself in a header, so the bar keeps its short `sheet_scores_title` beside back, close and the button.
struct SheetScoresContent: View {
    let subject: String
    let state: SheetScoresUiState
    var actions = SheetScoresContentActions()

    private var canGoBack: Bool {
        if state is SheetScoresUiStatePickTabRow { return true }
        if let total = state as? SheetScoresUiStatePickTotal { return total.page != .overview }
        return false
    }

    var body: some View {
        Text(verbatim: subject)
            .font(.itmo(.bodyMedium))
            .foregroundStyle(.secondary)
            .toolbar {
                if canGoBack {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(action: actions.back) {
                            AppSymbol.chevronLeft.image
                        }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("common_back")))
                        .accessibilityIdentifier("sheetScores.back")
                    }
                }
            }
        switch onEnum(of: state) {
        case .loading:
            ItmoLoadingView().frame(minHeight: 288)
        case let .failed(failed):
            let title = failed.status.text()?.resolved ?? AppStrings.string("sheet_scores_offline")
            if failed.status == .network {
                ItmoErrorView(title: title, retry: actions.retry)
            } else {
                ItmoEmptyView(symbol: .error, title: title, description: failed.status.reason()?.resolved)
            }
        case let .pickRow(pick):
            SheetRowChoice(
                rows: pick.candidates, note: AppStrings.string("sheet_scores_pick_row_note"), withTab: true,
                pick: actions.pickRow
            )
        case let .pickTabRow(pick):
            SheetRowChoice(rows: pick.rows, note: nil, withTab: false, pick: actions.pickRow)
        case let .pickTab(pick):
            SheetQuestion(title: AppStrings.string("sheet_scores_pick_tab"), note: AppStrings.string("sheet_scores_pick_tab_note"))
            ItmoListGroup(inset: 16) {
                ForEach(pick.tabs, id: \.gid) { tab in
                    ItmoRow(
                        title: tab.name.isEmpty ? AppStrings.string("sheet_scores_tab_untitled") : tab.name,
                        chevron: true
                    ) { actions.pickTab(tab) }
                }
            }
        case let .pickTotal(total):
            switch total.page {
            case .columns: SheetColumnsPage(state: total, pick: actions.pickColumn)
            case .sum: SheetSumPage(state: total, actions: actions)
            default: SheetTotalOverview(state: total, actions: actions)
            }
        case .done:
            EmptyView()
        }
    }
}

/// A step's question over the line that says why it is asked.
private struct SheetQuestion: View {
    let title: String
    var note: String?

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            ItmoSectionHeader(title: title)
            if let note {
                Text(verbatim: note)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, ItmoSpacing.related)
            }
        }
    }
}

/// `sheet_scores_pick_row`: the people to choose from; more than eight get the name search.
private struct SheetRowChoice: View {
    let rows: [SheetRowMatch]
    let note: String?
    let withTab: Bool
    let pick: (SheetRowMatch) -> Void
    @State private var query = ""

    var body: some View {
        SheetQuestion(title: AppStrings.string("sheet_scores_pick_row"), note: note)
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
                    ItmoRow(title: row.label, subtitle: withTab && !row.tab.name.isEmpty ? row.tab.name : nil) { pick(row) }
                }
            }
        }
    }
}

/// The total: the chosen one on top (or why there is none), then under `sheet_total_other` the other ready totals
/// with their values and the ways to another one; `sheet_scores_save` or `sheet_total_done` in the bar.
private struct SheetTotalOverview: View {
    let state: SheetScoresUiStatePickTotal
    let actions: SheetScoresContentActions

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            if let hero = state.hero {
                SheetTotalCard(
                    value: hero.value,
                    source: hero.source.resolved,
                    note: hero.note?.resolved,
                    warning: hero.warning,
                    official: hero.official?.resolved,
                    details: hero.details
                )
            } else {
                ItmoCard {
                    Text(verbatim: AppStrings.string("sheet_total_none")).font(.itmo(.titleMedium, emphasized: true))
                    Text(verbatim: state.missing.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("sheetScores.hero")
            }
            ItmoSectionHeader(title: AppStrings.string(state.hero == nil ? "sheet_total_find" : "sheet_total_other"))
            let variants = state.variants
            if !variants.isEmpty {
                ItmoListGroup(inset: 16) {
                    ForEach(variants.indices, id: \.self) { index in
                        let variant = variants[index]
                        ItmoRow(title: variant.title.resolved, subtitle: variant.subtitle?.resolved, trailing: variant.value) {
                            actions.select(variant.option)
                        }
                    }
                }
            }
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("sheet_total_pick_column"), symbol: .table, tone: .neutral, chevron: true,
                    action: actions.openColumns
                )
                .accessibilityIdentifier("sheetScores.columns")
                ItmoRow(
                    title: AppStrings.string("sheet_scores_sum_mode"), symbol: .add, tone: .neutral, chevron: true,
                    action: actions.openSum
                )
                .accessibilityIdentifier("sheetScores.sum")
                if let subtitle = aiSubtitle {
                    let running = state.ai is SheetAiStateRunning
                    ItmoRow(
                        title: AppStrings.string("sheet_ai_analyze"),
                        subtitle: subtitle,
                        symbol: .autoAwesome,
                        tone: .neutral,
                        action: running ? nil : actions.analyze
                    )
                    .accessory {
                        if running { ProgressView().itmoTint() }
                    }
                    .accessibilityIdentifier("sheetScores.ai")
                }
            }
        }
        .toolbar {
            if let action = state.action {
                ToolbarItem(placement: .confirmationAction) {
                    Button(AppStrings.string(action == .save ? "sheet_scores_save" : "sheet_total_done"), action: actions.save)
                        .accessibilityIdentifier("sheetScores.save")
                }
            }
        }
    }

    /// The AI row's line while it can be asked, runs or failed; nil hides the row (not offered, or its result is a
    /// variant).
    private var aiSubtitle: String? {
        switch onEnum(of: state.ai) {
        case .offer: AppStrings.string("sheet_ai_offer_note")
        case .running: AppStrings.string("sheet_ai_running")
        case let .failed(failed): failed.text.resolved
        case .hidden, .proposal: nil
        }
    }
}

/// The chosen total: `sheet_total_label`, the number, where it comes from, one note, the teacher's total and the
/// AI's details on demand.
private struct SheetTotalCard: View {
    let value: String?
    let source: String
    var note: String?
    var warning = false
    var official: String?
    var details: [String] = []
    @State private var showsDetails = false

    var body: some View {
        ItmoCard {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: AppStrings.string("sheet_total_label")).font(.itmo(.labelLarge)).foregroundStyle(.secondary)
                Text(verbatim: value ?? AppStrings.string("sheet_scores_empty_cell"))
                    .font(.itmo(.displaySmall))
                    .monospacedDigit()
                Text(verbatim: source).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
            if let note {
                Text(verbatim: note).font(.itmo(.bodyMedium)).foregroundStyle(warning ? ItmoColor.warning : .secondary)
            }
            if let official {
                Text(verbatim: official).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
            if !details.isEmpty {
                if showsDetails {
                    ForEach(details, id: \.self) { line in
                        Text(verbatim: line).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                    }
                }
                Button(AppStrings.string(showsDetails ? "sheet_ai_less" : "sheet_ai_more")) { showsDetails.toggle() }
                    .buttonStyle(.borderless)
                    .itmoTint()
                    .frame(minHeight: ItmoMetrics.touchTarget)
                    .accessibilityIdentifier("sheetScores.ai.details")
            }
        }
        .accessibilityIdentifier("sheetScores.hero")
    }
}

/// The tabs of a column page when the own row is in several.
private struct SheetTabChips: View {
    let tabs: [SheetTab]
    @Binding var index: Int

    var body: some View {
        if tabs.count > 1 {
            ItmoChips(chips: tabs.indices.map { at in
                ItmoChip(
                    label: tabs[at].name.isEmpty ? AppStrings.string("sheet_scores_tab_untitled") : tabs[at].name,
                    selected: at == index
                )
            }) { index = $0 }
        }
    }
}

/// `sheet_total_pick_column`: every column of the own row by tab, the chosen one checked, the empty ones folded; a
/// tap picks it and returns to the total.
private struct SheetColumnsPage: View {
    let state: SheetScoresUiStatePickTotal
    let pick: (SheetCell) -> Void
    @State private var tabIndex: Int?
    @State private var emptyOpen = false

    private var selected: SheetCell? { (state.selection as? TotalOptionColumn)?.cell }

    var body: some View {
        let tabs = state.tabs
        let index = tabIndex ?? selected.flatMap { cell in tabs.firstIndex { $0 == cell.tab } } ?? 0
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            ItmoSectionHeader(title: AppStrings.string("sheet_total_pick_column"))
            SheetTabChips(tabs: tabs, index: Binding(get: { index }, set: { tabIndex = $0 }))
            if tabs.indices.contains(index) {
                let tab = tabs[index]
                let filled = state.filledColumns(tab: tab)
                let empty = state.emptyColumns(tab: tab)
                if !filled.isEmpty {
                    ItmoListGroup(inset: 16) {
                        ForEach(filled, id: \.cell.key) { option in row(option.cell) }
                    }
                }
                if !empty.isEmpty {
                    ItmoListGroup(inset: 16) {
                        ItmoRow(
                            title: AppStrings.string("sheet_scores_empty_columns"),
                            trailing: "\(empty.count)",
                            action: { emptyOpen.toggle() }
                        )
                        .accessory {
                            AppSymbol.expandMore.image
                                .rotationEffect(.degrees(emptyOpen ? 180 : 0))
                                .foregroundStyle(.secondary)
                                .accessibilityHidden(true)
                        }
                        .accessibilityIdentifier("sheetScores.emptyColumns")
                        if emptyOpen {
                            ForEach(empty, id: \.cell.key) { option in row(option.cell) }
                        }
                    }
                }
            }
        }
    }

    private func row(_ cell: SheetCell) -> some View {
        let chosen = cell == selected
        return ItmoRow(
            title: SheetScoresUiStateKt.shortTitle(cell: cell).resolved,
            subtitle: SheetScoresUiStateKt.groupTitle(cell: cell)?.resolved,
            trailing: cell.value.isEmpty ? AppStrings.string("sheet_scores_empty_cell") : cell.value,
            action: { pick(cell) }
        )
        .accessory {
            AppSymbol.check.image
                .font(.itmo(.bodyLarge, emphasized: true))
                .foregroundStyle(ItmoColor.primary)
                .opacity(chosen ? 1 : 0)
                .accessibilityHidden(true)
        }
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }
}

/// `sheet_scores_sum_mode`: the live sum on top as the total it would be, then the columns with points, each with a
/// check; `sheet_total_done` in the bar once two are checked.
private struct SheetSumPage: View {
    let state: SheetScoresUiStatePickTotal
    let actions: SheetScoresContentActions
    @State private var tabIndex: Int?

    var body: some View {
        let tabs = state.tabs
        let index = tabIndex ?? state.summed.first.flatMap { cell in tabs.firstIndex { $0 == cell.tab } } ?? 0
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            ItmoSectionHeader(title: AppStrings.string("sheet_scores_sum_mode"))
            SheetTotalCard(value: state.sum, source: state.sumText?.resolved ?? AppStrings.string("sheet_scores_sum_hint"))
            SheetTabChips(tabs: tabs, index: Binding(get: { index }, set: { tabIndex = $0 }))
            if tabs.indices.contains(index) {
                let columns = state.sumColumns(tab: tabs[index])
                if !columns.isEmpty {
                    ItmoListGroup(inset: 16) {
                        ForEach(columns, id: \.cell.key) { option in sumRow(option.cell) }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(AppStrings.string("sheet_total_done"), action: actions.finishSum)
                    .disabled(!state.canFinishSum)
                    .accessibilityIdentifier("sheetScores.save")
            }
        }
    }

    private func sumRow(_ cell: SheetCell) -> some View {
        let checked = state.summed.contains(cell)
        return ItmoRow(
            title: SheetScoresUiStateKt.shortTitle(cell: cell).resolved,
            subtitle: SheetScoresUiStateKt.groupTitle(cell: cell)?.resolved,
            trailing: cell.value.isEmpty ? AppStrings.string("sheet_scores_empty_cell") : cell.value,
            action: { actions.toggleSum(cell) }
        )
        .accessory {
            AppSymbol.check.image
                .font(.itmo(.bodyLarge, emphasized: true))
                .foregroundStyle(ItmoColor.primary)
                .opacity(checked ? 1 : 0)
                .accessibilityHidden(true)
        }
        .accessibilityAddTraits(checked ? .isSelected : [])
    }
}

private extension SheetCell {
    /// The same cell across readings: its tab and column.
    var key: String { "\(tab.gid):\(column)" }
}
