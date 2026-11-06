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
            resetTotal: { viewModel.resetTotal() },
            summing: { viewModel.setSumming(on: $0) },
            save: { viewModel.save() },
            analyze: { viewModel.analyze() },
            useAi: { viewModel.useAi() },
            dismissAi: { viewModel.dismissAi() }
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
    var summing: (Bool) -> Void = { _ in }
    var save: () -> Void = {}
    var analyze: () -> Void = {}
    var useAi: () -> Void = {}
    var dismissAi: () -> Void = {}
}

/// The sheet without the ViewModel: the subject, then loading, a failure (a retry only without a connection), the
/// rows or tabs to choose from, or the total's review. Stateless but for the name search and the open column list.
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
/// colour) or a saved sum in words, `sheet_ai_analyze` and its result for a shared sheet, the own row around it, the
/// candidates (the automatic one marked) and `sheet_scores_other_cell` as chips, `sheet_scores_reset_auto` for a
/// hand-picked total. The other-cell chip, or a review without a single-cell total, opens the column list: the
/// `sheet_scores_sum_mode` chip, the tab switcher, every column with the own value and how full it is, the empty ones
/// folded. A tap selects; `sheet_scores_save` in the bar saves.
private struct SheetTotalReview: View {
    let state: SheetScoresUiStatePickTotal
    let actions: SheetScoresContentActions
    @State private var picker = false
    @State private var tabIndex = 0
    @State private var emptyOpen = false

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
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
            } else if let saved = state.saved {
                ItmoCard(tone: .primary) {
                    Text(verbatim: saved.value ?? AppStrings.string("sheet_scores_empty_cell"))
                        .font(.itmo(.headlineMedium, emphasized: true))
                        .monospacedDigit()
                    Text(verbatim: saved.formula.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                    if let official = saved.official {
                        Text(verbatim: AppStrings.string("sheet_scores_official", [official]))
                            .font(.itmo(.bodySmall))
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
            SheetAiBlock(ai: state.ai, actions: actions)
            if let shown = state.shown {
                SheetRowPreview(cells: state.preview, shown: shown, pick: actions.pickTotal)
            }
            if state.shown != nil || state.saved != nil {
                let candidates = state.candidates
                ItmoChips(
                    chips: candidates.map {
                        ItmoChip(label: state.chip(cell: $0).resolved, selected: !state.summing && $0 == state.shown)
                    } + [ItmoChip(label: AppStrings.string("sheet_scores_other_cell"), selected: picker)],
                    wrap: true
                ) { index in
                    if index < candidates.count { actions.pickTotal(candidates[index]) } else { picker.toggle() }
                }
            }
            if state.canReset {
                Button(AppStrings.string("sheet_scores_reset_auto"), action: actions.resetTotal)
                    .buttonStyle(.bordered)
                    .itmoTint()
                    .accessibilityIdentifier("sheetScores.reset")
            }
            if picker || (state.shown == nil && state.saved == nil) || state.summing {
                columnPicker
            }
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(AppStrings.string("sheet_scores_save"), action: actions.save)
                    .disabled(!state.canSave)
                    .accessibilityIdentifier("sheetScores.save")
            }
        }
    }

    @ViewBuilder private var columnPicker: some View {
        if state.cells.allSatisfy({ $0.value.isEmpty }) {
            ItmoGroupNote(text: AppStrings.string("sheet_scores_no_values"))
        }
        ItmoChips(chips: [ItmoChip(label: AppStrings.string("sheet_scores_sum_mode"), selected: state.summing)]) { _ in
            actions.summing(!state.summing)
        }
        .accessibilityIdentifier("sheetScores.sumMode")
        if state.summing {
            if let sum = state.sum {
                Text(verbatim: AppStrings.string("sheet_formula_sum", [sum]))
                    .font(.itmo(.titleMedium, emphasized: true))
                    .monospacedDigit()
            } else {
                Text(verbatim: AppStrings.string("sheet_scores_sum_hint")).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
        }
        let tabs = state.tabs
        if tabs.count > 1 {
            ItmoChips(chips: tabs.indices.map { index in
                ItmoChip(
                    label: tabs[index].name.isEmpty ? AppStrings.string("sheet_scores_pick_tab") : tabs[index].name,
                    selected: index == selectedTab
                )
            }) { tabIndex = $0 }
        }
        if let tab = tabs.indices.contains(selectedTab) ? tabs[selectedTab] : nil {
            let filled = state.filledColumns(tab: tab)
            let empty = state.emptyColumns(tab: tab)
            if !filled.isEmpty {
                ItmoListGroup(inset: 16) {
                    ForEach(filled, id: \.cell.key) { option in columnRow(option) }
                }
            }
            if !empty.isEmpty {
                ItmoListGroup(inset: 16) {
                    ItmoRow(
                        title: AppStrings.string("sheet_scores_empty_columns", [Int64(empty.count)]),
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
                        ForEach(empty, id: \.cell.key) { option in columnRow(option) }
                    }
                }
            }
        }
    }

    private var selectedTab: Int { state.tabs.indices.contains(tabIndex) ? tabIndex : 0 }

    /// One column: its header path over how full it is, the own value, a check when it is selected or summed.
    private func columnRow(_ option: SheetColumnOption) -> some View {
        let cell = option.cell
        let selected = state.summing ? state.summed.contains(cell) : cell == state.shown
        return ItmoRow(
            title: SheetScoresUiStateKt.title(cell: cell).resolved,
            subtitle: option.summary.resolved,
            trailing: cell.value.isEmpty ? AppStrings.string("sheet_scores_empty_cell") : cell.value,
            action: { actions.pickTotal(cell) }
        )
        .accessory {
            AppSymbol.check.image
                .font(.itmo(.bodyLarge, emphasized: true))
                .foregroundStyle(ItmoColor.primary)
                .opacity(selected ? 1 : 0)
                .accessibilityHidden(true)
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func caption(_ cell: SheetCell) -> String {
        let path = SheetScoresUiStateKt.title(cell: cell).resolved.replacingOccurrences(of: " · ", with: " › ")
        return cell.tab.name.isEmpty ? path : AppStrings.string("sheet_scores_caption", [path, cell.tab.name])
    }
}

/// `sheet_ai_analyze`: the button, its progress while Backend analyses the sheet, the proposal (the formula in words,
/// the own value, the teacher's total, the model's summary and warnings, `sheet_ai_use` / `sheet_ai_dismiss`) or why
/// there is none.
private struct SheetAiBlock: View {
    let ai: SheetAiState
    let actions: SheetScoresContentActions
    @State private var showsDetails = false

    var body: some View {
        switch onEnum(of: ai) {
        case .hidden:
            EmptyView()
        case .offer, .running, .failed:
            VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                ItmoProgressButton(
                    title: AppStrings.string("sheet_ai_analyze"),
                    symbol: .autoAwesome,
                    isInProgress: ai is SheetAiStateRunning,
                    action: actions.analyze
                )
                .accessibilityIdentifier("sheetScores.ai")
                if ai is SheetAiStateRunning {
                    Text(verbatim: AppStrings.string("sheet_ai_running") + " " + AppStrings.string("sheet_ai_running_note"))
                        .font(.itmo(.bodySmall))
                        .foregroundStyle(.secondary)
                } else if let failed = ai as? SheetAiStateFailed {
                    Text(verbatim: failed.text.resolved).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                }
            }
        case let .proposal(proposal):
            ItmoCard(tone: .primary) {
                Text(verbatim: AppStrings.string("sheet_ai_title"))
                    .font(.itmo(.titleSmall, emphasized: true))
                    .foregroundStyle(ItmoColor.primary)
                    .accessibilityAddTraits(.isHeader)
                Text(verbatim: proposal.value ?? AppStrings.string("sheet_scores_empty_cell"))
                    .font(.itmo(.headlineMedium, emphasized: true))
                    .monospacedDigit()
                Text(verbatim: proposal.formula.resolved).font(.itmo(.bodyMedium))
                if proposal.hasOfficial {
                    Text(verbatim: AppStrings.string(
                        "sheet_scores_official", [proposal.official ?? AppStrings.string("sheet_scores_empty_cell")]
                    ))
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                }
                Text(verbatim: AppStrings.string("sheet_ai_check")).font(.itmo(.bodySmall)).foregroundStyle(ItmoColor.warning)
                if !proposal.summary.isEmpty || !proposal.warnings.isEmpty {
                    if showsDetails {
                        if !proposal.summary.isEmpty {
                            Text(verbatim: proposal.summary).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                        }
                        ForEach(proposal.warnings, id: \.self) { warning in
                            Text(verbatim: warning).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                        }
                    }
                    Button(AppStrings.string(showsDetails ? "sheet_ai_less" : "sheet_ai_more")) { showsDetails.toggle() }
                        .buttonStyle(.borderless)
                        .itmoTint()
                        .frame(minHeight: ItmoMetrics.touchTarget)
                        .accessibilityIdentifier("sheetScores.ai.details")
                }
                HStack(spacing: ItmoSpacing.compact) {
                    ItmoProgressButton(title: AppStrings.string("sheet_ai_use"), action: actions.useAi)
                        .accessibilityIdentifier("sheetScores.ai.use")
                    Button(AppStrings.string("sheet_ai_dismiss"), action: actions.dismissAi)
                        .buttonStyle(.bordered)
                        .itmoTint()
                        .frame(minHeight: ItmoMetrics.touchTarget)
                        .accessibilityIdentifier("sheetScores.ai.dismiss")
                }
                .padding(.top, ItmoSpacing.related)
            }
        }
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
