import Shared
import SwiftUI

/// The recordbook tab's root (docs/features/recordbook.md) over the shared `RecordbookViewModel`: the root header
/// with the period's subtitle and the period menu, then the list. A subject opens its page, physical education the
/// sport tab; the offer to connect BARS and an ended BARS session open `BarsLoginSheet`, after which the list loads
/// again with BARS connected. The BARS reminder's tap asks the router for the sign-in, which this root shows.
struct RecordbookScreen: View {
    let router: AppRouter
    @State private var model = ObservableViewModel(RecordbookViewModel.self, state: \.uiState)
    @State private var showsBarsLogin = false

    var body: some View {
        RecordbookContent(
            state: model.state,
            actions: RecordbookContentActions(
                open: open,
                barsSignIn: { showsBarsLogin = true },
                retry: { model.viewModel.refresh(mode: .force) },
                hideOffer: { model.viewModel.hideBarsOffer() },
                openSources: { model.viewModel.openSources(id: $0) }
            )
        )
        .refreshable {
            await model.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                ($0.uiState.value.body as? RecordbookBodyContent)?.refreshing == true
            })
        }
        .itmoRootHeader(title: ShellTab.recordbook.title, subtitle: model.state.subtitle?.resolved) {
            if let periods = model.state.periods {
                RecordbookPeriodMenu(periods: periods) { programId, semester in
                    model.viewModel.selectPeriod(programId: programId, semester: semester)
                }
            }
        }
        .sheet(item: sourcesBinding) { sources in
            RecordbookSourcesSheet(sources: sources) {
                model.viewModel.closeSources()
                open(sources.target)
            }
        }
        .sheet(isPresented: $showsBarsLogin) {
            BarsLoginSheet {
                showsBarsLogin = false
                model.viewModel.barsSignedIn()
            }
        }
        .observing(model)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.recordbook")
        .onAppear { model.viewModel.refresh(mode: .silent) }
        .onChange(of: router.barsLoginRequested, initial: true) {
            if router.consumeBarsLogin() { showsBarsLogin = true }
        }
    }

    private var sourcesBinding: Binding<RecordbookSources?> {
        Binding(
            get: { (model.state.body as? RecordbookBodyContent)?.sources },
            set: { if $0 == nil { model.viewModel.closeSources() } }
        )
    }

    private func open(_ target: RecordbookTarget) {
        switch onEnum(of: target) {
        case let .subject(subject):
            if let args = subject.args.validOrNull() {
                router.open(AppRoutes.RecordbookSubject(args: args, materials: false))
            }
        case .sport:
            router.selectedTab = .sport
        }
    }
}

extension RecordbookSources: @retroactive Identifiable {}

/// What the list asks its host to do.
struct RecordbookContentActions {
    var open: (RecordbookTarget) -> Void = { _ in }
    var barsSignIn: () -> Void = {}
    var retry: () -> Void = {}
    var hideOffer: () -> Void = {}
    var openSources: (Int64) -> Void = { _ in }
}

/// The list without the ViewModel: one list with the session's hero near the end of the semester, a BARS notice, the
/// sections with each number over its quiet source, the quiet BARS offer; or loading, empty and the error with retry.
struct RecordbookContent: View {
    let state: RecordbookUiState
    var actions = RecordbookContentActions()

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state.body) {
            case .loading:
                ItmoLoadingView()
                    .padding(.top, ItmoSpacing.section)
                    .accessibilityIdentifier("recordbook.loading")
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(failed.error).resolved,
                    retry: actions.retry
                )
                .accessibilityIdentifier("recordbook.error")
            case .empty:
                ItmoEmptyView(
                    symbol: .book2,
                    title: AppStrings.string("recordbook_empty_title"),
                    description: AppStrings.string("recordbook_empty_description")
                )
                .accessibilityIdentifier("recordbook.empty")
            case let .content(content):
                list(content)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.list")
    }

    @ViewBuilder
    private func list(_ content: RecordbookBodyContent) -> some View {
        if let error = content.refreshError {
            ItmoGroupNote(text: AppStrings.string("recordbook_refresh_error", [AppErrorTextsKt.toUiText(error).resolved]))
        }
        if let summary = content.summary {
            RecordbookSessionHero(summary: summary)
        }
        if let notice = content.barsNotice {
            RecordbookBarsNoticeRow(notice: notice, signIn: actions.barsSignIn, retry: actions.retry)
        }
        ForEach(content.sections, id: \.self) { section in
            ItmoSectionHeader(title: section.title.resolved)
            ItmoListGroup(inset: ItmoKit.padding) {
                ForEach(section.rows, id: \.id) { row in
                    RecordbookRowView(row: row, open: actions.open, openSources: actions.openSources)
                }
            }
        }
        if content.barsOffer {
            ItmoListGroup {
                RecordbookBarsOfferRow(connect: actions.barsSignIn, hide: actions.hideOffer)
            }
            .padding(.top, ItmoSpacing.compact)
            .accessibilityIdentifier("recordbook.barsOffer")
        }
    }
}

/// The period ("3rd semester") with a chevron and the menu of every period, the chosen one checked; a programme is named when there are several.
struct RecordbookPeriodMenu: View {
    let periods: RecordbookPeriods
    let select: (Int64, Int32) -> Void

    var body: some View {
        Menu {
            ForEach(periods.groups, id: \.program) { group in
                Section {
                    ForEach(group.options, id: \.semester) { option in
                        Button {
                            select(option.programId, option.semester)
                        } label: {
                            if option.selected {
                                Label(option.title.resolved, systemImage: AppSymbol.check.systemName)
                            } else {
                                Text(verbatim: option.title.resolved)
                            }
                            Text(verbatim: option.detail.resolved)
                        }
                    }
                } header: {
                    if let program = group.program { Text(verbatim: program) }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(verbatim: periods.label.resolved)
                AppSymbol.expandMore.image.font(.itmo(.labelSmall, emphasized: true))
            }
        }
        .buttonStyle(.glass)
        .itmoTint()
        .accessibilityLabel(Text(verbatim: AppStrings.string("recordbook_period_label")))
        .accessibilityValue(Text(verbatim: periods.label.resolved))
        .accessibilityIdentifier("recordbook.period")
    }
}

/// The session: "passed 3 of 6" over the passed share and the average; the one hero of the list.
struct RecordbookSessionHero: View {
    let summary: RecordbookSummary

    var body: some View {
        ItmoCard(tone: .primary, hero: true) {
            Text(verbatim: AppStrings.string("recordbook_session_title"))
                .font(.itmo(.labelLarge, emphasized: true))
                .foregroundStyle(ItmoColor.primary)
            HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.compact) {
                Text(verbatim: summary.passed.resolved)
                    .font(.itmo(.displaySmall, emphasized: true))
                    .monospacedDigit()
                Text(verbatim: summary.of.resolved).foregroundStyle(.secondary)
            }
            ItmoProgressBar(value: Double(summary.share)).padding(.vertical, 4)
            if let average = summary.average {
                Text(verbatim: average.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("recordbook.summary")
    }
}

/// BARS over a list: one quiet row with sign-in (the session ended) or retry, never a card.
struct RecordbookBarsNoticeRow: View {
    let notice: RecordbookBarsNotice
    let signIn: () -> Void
    let retry: () -> Void

    var body: some View {
        let failed = notice as? RecordbookBarsNoticeFailed
        ItmoListGroup {
            RecordbookBarsRow(
                title: AppStrings.string(failed == nil ? "recordbook_bars_expired" : "recordbook_bars_failed"),
                subtitle: failed.map { AppErrorTextsKt.toUiText($0.error).resolved },
                symbol: failed == nil ? .history : .error,
                tone: .warn,
                action: AppStrings.string(failed == nil ? "recordbook_bars_sign_in" : "common_retry"),
                perform: failed == nil ? signIn : retry
            )
        }
        .accessibilityIdentifier("recordbook.barsNotice")
    }
}

/// The BARS offer ("checkpoints and minimums from BARS") with connect; with `hide`, the cross that hides it for good.
struct RecordbookBarsOfferRow: View {
    let connect: () -> Void
    var hide: (() -> Void)?

    var body: some View {
        RecordbookBarsRow(
            title: AppStrings.string("recordbook_bars_offer"),
            symbol: .assignment,
            tone: .neutral,
            action: AppStrings.string("recordbook_bars_connect"),
            perform: connect,
            hide: hide
        )
    }
}

/// A BARS row of a group, top-aligned as a multi-line list row: the icon tile, the title and subtitle over the action
/// and the cross at the end with `hide` (as tall as the tile) share the top edge. The text takes the whole width but the cross and the button sits under it, so a narrow phone or a large
/// Dynamic Type size never squeezes the title into a column that breaks inside words.
private struct RecordbookBarsRow: View {
    let title: String
    var subtitle: String?
    let symbol: AppSymbol
    let tone: ItmoTone
    let action: String
    let perform: () -> Void
    var hide: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: ItmoSpacing.content) {
            ItmoRowIcon(symbol: symbol, tone: tone)
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: title).font(.itmo(.bodyLarge))
                if let subtitle {
                    Text(verbatim: subtitle).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
                Button(action, action: perform)
                    .buttonStyle(.borderless)
                    .itmoTint()
                    .padding(.top, ItmoSpacing.related)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let hide {
                Button(action: hide) {
                    AppSymbol.close.image
                        .font(.itmo(.bodySmall, emphasized: true))
                        .foregroundStyle(.secondary)
                        // As tall as the tile, with a 44 pt hit area around it.
                        .frame(width: 30, height: 30)
                        .contentShape(.rect.inset(by: -7))
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(verbatim: AppStrings.string("recordbook_bars_hide")))
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        // The row is one VoiceOver element: its buttons are its actions.
        .accessibilityElement(children: .combine)
        .accessibilityActions {
            Button(action, action: perform)
            if let hide { Button(AppStrings.string("recordbook_bars_hide"), action: hide) }
        }
    }
}

/// A subject: name and reason or kind, the new-mark dot, the number over its source (a tap there opens the sources),
/// the chevron. The number moves under the name when beside it a word would not fit.
struct RecordbookRowView: View {
    let row: RecordbookRow
    var open: (RecordbookTarget) -> Void = { _ in }
    var openSources: (Int64) -> Void = { _ in }

    private static let nameFont = Font.itmo(.titleMedium, emphasized: true)
    private static let metaFont = Font.itmo(.bodyMedium)

    var body: some View {
        let meta = row.meta.resolved
        HStack(spacing: ItmoSpacing.content) {
            RecordbookNameAndValue(spacing: ItmoSpacing.content) {
                Button { open(row.target) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: row.name)
                            .font(Self.nameFont)
                            .foregroundStyle(Color.primary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                        Text(verbatim: meta)
                            .font(Self.metaFont)
                            .foregroundStyle(row.metaTone.kit.color)
                            .multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                value
                // Every word, unseen: the layout keeps the widest one unbroken.
                ForEach(Array(Self.words(row.name).enumerated()), id: \.offset) { Self.measured($0.element, Self.nameFont) }
                ForEach(Array(Self.words(meta).enumerated()), id: \.offset) { Self.measured($0.element, Self.metaFont) }
            }
            AppSymbol.chevronRight.image
                .font(.itmo(.bodySmall, emphasized: true))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.row.\(row.id)")
    }

    /// The new-mark dot and the number over its source.
    private var value: some View {
        HStack(spacing: ItmoSpacing.content) {
            if row.unread {
                Circle().fill(ItmoColor.primary).frame(width: 8, height: 8)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("recordbook_subject_new")))
            }
            let source = row.source.resolved
            ItmoValueWithSource(
                source: source,
                stale: row.stale,
                onSource: row.hasSources ? { openSources(row.id) } : nil,
                sourceLabel: AppStrings.string("recordbook_sources_action", [source])
            ) {
                RecordbookValueView(value: row.value)
            }
            .itmoLoadingPulse(row.pending, label: AppStrings.string("recordbook_bars_loading"))
        }
    }

    private static func words(_ text: String) -> [String] {
        text.split(separator: " ").map(String.init)
    }

    private static func measured(_ word: String, _ font: Font) -> some View {
        Text(verbatim: word).font(font).fixedSize().hidden().accessibilityHidden(true)
    }
}

/// The name (first subview) beside the value (second) while the widest of the other subviews, single words, still fits
/// beside it; otherwise the value goes under the name at the trailing edge, so a narrow phone or a large Dynamic Type
/// size never breaks a word. The word subviews only measure.
private struct RecordbookNameAndValue: Layout {
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let arranged = arrange(proposal.width, subviews)
        return CGSize(width: arranged.width, height: arranged.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let arranged = arrange(bounds.width, subviews)
        subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY + arranged.nameY), proposal: ProposedViewSize(arranged.name))
        let value = CGPoint(x: bounds.maxX - arranged.value.width, y: bounds.minY + arranged.valueY)
        subviews[1].place(at: value, proposal: ProposedViewSize(arranged.value))
        for word in subviews.dropFirst(2) { word.place(at: bounds.origin, proposal: .unspecified) }
    }

    private struct Arranged {
        var width: CGFloat
        var height: CGFloat
        var name: CGSize
        var nameY: CGFloat
        var value: CGSize
        var valueY: CGFloat
    }

    private func arrange(_ proposed: CGFloat?, _ subviews: Subviews) -> Arranged {
        let value = subviews[1].sizeThatFits(.unspecified)
        let longest = subviews.dropFirst(2).map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        let width = proposed ?? (subviews[0].sizeThatFits(.unspecified).width + spacing + value.width)
        let beside = width - value.width - spacing
        if beside >= longest {
            let name = subviews[0].sizeThatFits(ProposedViewSize(width: beside, height: nil))
            let height = max(name.height, value.height)
            return Arranged(
                width: width, height: height,
                name: CGSize(width: beside, height: name.height), nameY: (height - name.height) / 2,
                value: value, valueY: (height - value.height) / 2
            )
        }
        let name = subviews[0].sizeThatFits(ProposedViewSize(width: width, height: nil))
        return Arranged(
            width: width, height: name.height + spacing + value.height,
            name: CGSize(width: width, height: name.height), nameY: 0,
            value: value, valueY: name.height + spacing
        )
    }
}

/// A row's number: points over a meter, a result pill, the sport ring or a dash.
struct RecordbookValueView: View {
    let value: RecordbookValue

    var body: some View {
        switch onEnum(of: value) {
        case let .points(points):
            ItmoPointsMeter(text: points.text, share: Double(points.share), tone: points.tone.kit)
        case let .result(result):
            ItmoStatusPill(pill: ItmoPill(text: result.text.resolved, tone: result.tone.kit))
        case let .sport(sport):
            ItmoSportRing(text: sport.text, visits: Double(sport.visits), bonus: Double(sport.bonus))
        case .none:
            Text(verbatim: AppStrings.string("recordbook_score_pending"))
                .font(.itmo(.titleLarge))
                .foregroundStyle(.secondary)
        }
    }
}

extension RecordbookTone {
    /// The kit's tone of a shared one.
    var kit: ItmoTone {
        switch self {
        case .neutral: .neutral
        case .primary: .primary
        case .ok: .ok
        case .warn: .warn
        case .bad: .bad
        }
    }
}
