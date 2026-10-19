import Shared
import SwiftUI

/// The sport tab's root (docs/features/sport.md) over the shared `SportMyViewModel` and `SportSignViewModel`: the
/// static root header, "mine" and "sign-up" as a segmented choice, then the chosen part. A lesson opens its sheet
/// (`SportLessonSheet`) whose action goes back to the part that opened it; the more-filters and the queue's sheets
/// and questions open from the state. A lesson the router hands the tab (`TabRequest.SportLesson`, from a
/// `/sport/<id>` link or a sport notification) selects sign-up and opens there.
struct SportTabScreen: View {
    let router: AppRouter
    @State private var my = ObservableViewModel(SportMyViewModel.self, state: \.uiState)
    @State private var sign = ObservableViewModel(SportSignViewModel.self, state: \.uiState)
    @State private var tab: SportTab = .my
    @State private var lesson: SportLessonItem?
    @State private var message: SettingsMessage?

    var body: some View {
        SportContent(tab: $tab, my: my.state, sign: sign.state, actions: actions)
            .refreshable {
                switch tab {
                case .my:
                    await my.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                        ($0.uiState.value.body as? SportMyBodyContent)?.refreshing == true
                    })
                case .sign:
                    await sign.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                        ($0.uiState.value.body as? SportSignBodyDay)?.refreshing == true
                    })
                }
            }
            .itmoRootHeader(title: ShellTab.sport.title, subtitle: nil)
            .sheet(item: $lesson) { item in
                SportLessonSheet(item: item, present: sheet(of: item)) { action in
                    lesson = nil
                    switch item.from {
                    case .my: my.viewModel.onSheetAction(lessonId: item.details.lessonId, action: action)
                    case .sign: sign.viewModel.onSheetAction(lessonId: item.details.lessonId, action: action)
                    }
                } openProfile: { isu in
                    lesson = nil
                    router.open(AppRoutes.UserProfile(isu: isu))
                } section: {
                    my.viewModel.sectionToggle(lessonId: item.details.lessonId)
                } toggleSection: { my.viewModel.toggleSection(lessonId: $0) }
            }
            .sheet(isPresented: filtersShown) {
                if let filters = sign.state.filters {
                    SportFiltersSheet(filters: filters, actions: filterActions)
                }
            }
            .sheet(isPresented: joinShown) {
                if let join = sign.state.prompt as? SportPromptJoin {
                    SportJoinSheet(join: join) { sign.viewModel.confirmPrompt(lateSign: $0) }
                }
            }
            .sportPromptAlert(sign.state.prompt, confirm: { sign.viewModel.confirmPrompt(lateSign: false) }) {
                sign.viewModel.dismissPrompt()
            }
            .sportCancelAlert(my.state.cancel, confirm: { my.viewModel.confirmCancel() }) {
                my.viewModel.dismissCancel()
            }
            .overlay(alignment: .bottom) {
                if let message {
                    SettingsMessageBanner(text: message.text)
                        .task(id: message.id) {
                            try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                            if self.message?.id == message.id { self.message = nil }
                        }
                }
            }
            .animation(.default, value: message?.id)
            .observing(my)
            .observing(sign)
            .onEvents(of: my, \.events) { say($0.resolved) }
            .onEvents(of: sign, \.events) { event in
                switch onEnum(of: event) {
                case let .notice(notice): say(notice.text.resolved)
                case let .openLesson(open): openLesson(open.lessonId, from: .sign)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("shell.root.sport")
            .onAppear { my.viewModel.ensureDataLoaded() }
            .onChange(of: router.hasRequest(of: .sport), initial: true) { _, waiting in
                guard waiting, let request = router.consumeRequest(of: .sport) as? TabRequestSportLesson else { return }
                lesson = nil
                tab = .sign
                sign.viewModel.openSharedLesson(lessonId: request.lessonId, predicted: request.predicted)
            }
    }

    private var actions: SportContentActions {
        SportContentActions(
            openPoints: { router.open(AppRoutes.SportPoints.shared) },
            openBooking: { openLesson($0, from: .my) },
            stopSection: { my.viewModel.stopSection(sectionId: $0) },
            sectionDate: { my.viewModel.setSectionDate(sectionId: $0, index: Int32($1), on: $2) },
            retryMy: { my.viewModel.refresh(mode: .force) },
            query: { sign.viewModel.setQuery(query: $0) },
            chip: { sign.viewModel.toggle(chip: $0) },
            day: { sign.viewModel.selectDate(date: $0) },
            week: { sign.viewModel.showWeek(index: Int32($0)) },
            openFilters: { sign.viewModel.openFilters() },
            empty: { sign.viewModel.onEmptyAction(action: $0) },
            resetFilters: { sign.viewModel.resetFilters() },
            openLesson: { openLesson($0, from: .sign) },
            rowAction: { sign.viewModel.act(lessonId: $0) },
            retrySign: { sign.viewModel.refresh(mode: .force) }
        )
    }

    private var filterActions: SportFilterActions {
        let viewModel = sign.viewModel
        return SportFilterActions(
            section: { viewModel.toggleSection(name: $0) },
            moreSections: { viewModel.showAllSections() },
            sectionQuery: { viewModel.setSectionQuery(query: $0) },
            building: { viewModel.selectBuilding(name: $0) },
            teacher: { viewModel.selectTeacher(name: $0) },
            dayPart: { viewModel.selectDayPart(part: $0) },
            onlyAvailable: { viewModel.setOnlyAvailable(on: $0) },
            onlyFree: { viewModel.setOnlyFree(on: $0) },
            onlyPinned: { viewModel.setOnlyPinned(on: $0) },
            onlyFriends: { viewModel.setOnlyFriends(on: $0) },
            reset: { viewModel.resetFilters() }
        )
    }

    private var filtersShown: Binding<Bool> {
        Binding(get: { tab == .sign && sign.state.filters != nil }, set: { if !$0 { sign.viewModel.closeFilters() } })
    }

    private var joinShown: Binding<Bool> {
        Binding(get: { sign.state.prompt is SportPromptJoin }, set: { if !$0 { sign.viewModel.dismissPrompt() } })
    }

    private func openLesson(_ id: Int64, from part: SportTab) {
        let details = part == .my ? my.viewModel.details(lessonId: id) : sign.viewModel.details(lessonId: id)
        guard let details else { return }
        lesson = SportLessonItem(details: details, from: part)
    }

    private func sheet(of item: SportLessonItem) -> (Bool, TeacherLevel?) -> SportLessonSheetUi {
        let my = my.viewModel
        let sign = sign.viewModel
        return { submitted, level in
            item.from == .my
                ? my.sheet(details: item.details, submitted: submitted, teacherLevel: level)
                : sign.sheet(details: item.details, submitted: submitted, teacherLevel: level)
        }
    }

    private func say(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}

/// One opening of the lesson sheet: a new identity for every opening, the part it came from.
struct SportLessonItem: Identifiable {
    let id = UUID()
    let details: SportCommonDetailsArgs
    let from: SportTab
}

/// What the tab's parts ask their host to do.
struct SportContentActions {
    var openPoints: () -> Void = {}
    var openBooking: (Int64) -> Void = { _ in }
    var stopSection: (Int64) -> Void = { _ in }
    var sectionDate: (Int64, Int, Bool) -> Void = { _, _, _ in }
    var retryMy: () -> Void = {}
    var query: (String) -> Void = { _ in }
    var chip: (SportQuickFilter) -> Void = { _ in }
    var day: (Kotlinx_datetimeLocalDate) -> Void = { _ in }
    var week: (Int) -> Void = { _ in }
    var openFilters: () -> Void = {}
    var empty: (SportEmptyAction) -> Void = { _ in }
    var resetFilters: () -> Void = {}
    var openLesson: (Int64) -> Void = { _ in }
    var rowAction: (Int64) -> Void = { _ in }
    var retrySign: () -> Void = {}
}

/// The tab without the ViewModels: the segmented choice over a pager of Mine and Sign-up, so a horizontal swipe
/// switches them too. The shown part's list is `sport.list`.
struct SportContent: View {
    @Binding var tab: SportTab
    let my: SportMyUiState
    let sign: SportSignUiState
    var actions = SportContentActions()
    @State private var page: SportTab?

    private static let parts = [SportTab.my, SportTab.sign]

    var body: some View {
        VStack(spacing: ItmoKit.gap) {
            ItmoSegmentedChoice(
                label: AppStrings.string("title_sport"),
                options: Self.parts,
                selection: $tab
            ) { AppStrings.string($0 == .my ? "sport_tab_my" : "sport_tab_sign") }
            .accessibilityIdentifier("sport.tabs")
            .padding(.horizontal, ItmoKit.margin)
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(Self.parts, id: \.self) { part in
                        ItmoScreenStack {
                            switch part {
                            case .my: SportMyPart(state: my, actions: actions) { tab = .sign }
                            default: SportSignPart(state: sign, actions: actions)
                            }
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier(part == tab ? "sport.list" : "sport.list.hidden")
                        .containerRelativeFrame(.horizontal)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $page)
            .scrollIndicators(.hidden)
        }
        .background(Color(.systemGroupedBackground))
        .onChange(of: tab, initial: true) { _, tab in
            guard page != tab else { return }
            withAnimation(.smooth) { page = tab }
        }
        .onChange(of: page) { _, page in
            if let page, page != tab { tab = page }
        }
    }
}

/// Mine: the score ring that opens Where the points come from, the bookings by day as lesson cards, then the sections
/// with their next dates. "Make it a section" lives in a booked lesson's sheet.
struct SportMyPart: View {
    let state: SportMyUiState
    let actions: SportContentActions
    let findLesson: () -> Void

    var body: some View {
        if let score = state.score {
            Button(action: actions.openPoints) { SportScoreCard(score: score) }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: AppStrings.string("sport_points_title")))
                .accessibilityValue(Text(verbatim: "\(score.total), \(score.formula.resolved), \(score.status.resolved)"))
                .accessibilityIdentifier("sport.score")
        }
        switch onEnum(of: state.body) {
        case .loading:
            ItmoLoadingView().accessibilityIdentifier("sport.loading")
        case let .failed(failed):
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(failed.error).resolved,
                retry: actions.retryMy
            )
            .accessibilityIdentifier("sport.error")
        case let .content(content):
            ItmoSectionHeader(title: AppStrings.string("sport_bookings_title"))
            if content.days.isEmpty {
                ItmoEmptyView(
                    symbol: .sprint,
                    title: AppStrings.string("sport_bookings_empty_title"),
                    description: AppStrings.string("sport_bookings_empty_description"),
                    action: (AppStrings.string("sport_bookings_open_schedule"), findLesson)
                )
                .accessibilityIdentifier("sport.empty")
            }
            ForEach(content.days, id: \.self) { day in
                SportDayLabel(text: day.title.resolved)
                ForEach(day.bookings, id: \.lessonId) { booking in
                    Button { actions.openBooking(booking.lessonId) } label: {
                        ItmoLessonCard(lesson: booking.lesson.kitLesson(id: "\(booking.lessonId)"))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("sport.booking.\(booking.lessonId)")
                }
            }
        }
        if !state.sections.isEmpty {
            ItmoSectionHeader(title: AppStrings.string("sport_sections_title"))
                .padding(.top, ItmoSpacing.related)
                .accessibilityIdentifier("sport.sections")
            ForEach(state.sections, id: \.id) { section in
                SportSectionGroup(section: section, actions: actions)
            }
            ItmoGroupNote(text: AppStrings.string("sport_section_note"))
                .accessibilityIdentifier("sport.sections.note")
        }
    }
}

/// A section as one group. The head is the sport with the slot, the teacher and the hall as symbol lines, the place in
/// the section queue as a pill and the off switch; then the next dates, each with its status pill and its own switch.
struct SportSectionGroup: View {
    let section: SportSectionCardUi
    let actions: SportContentActions

    var body: some View {
        ItmoListGroup(inset: ItmoKit.padding) {
            HStack(spacing: ItmoSpacing.content) {
                VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                    Text(verbatim: section.title).font(.itmo(.titleMedium, emphasized: true))
                    SportMetaLine(symbol: .schedule, text: section.slot.resolved)
                    if let teacher = section.teacher { SportMetaLine(symbol: .person, text: teacher) }
                    if let place = section.place { SportMetaLine(symbol: .locationOn, text: place.resolved) }
                    ItmoStatusPill(pill: ItmoPill(text: section.position.resolved, tone: .primary, symbol: .group))
                }
                .accessibilityElement(children: .combine)
                Spacer(minLength: 0)
                Toggle(isOn: Binding(get: { true }, set: { if !$0 { actions.stopSection(section.id) } })) {
                    Text(verbatim: AppStrings.string("sport_section_off"))
                }
                .labelsHidden()
                .disabled(section.busy)
                .itmoTint()
            }
            .padding(ItmoKit.padding)
            ForEach(Array(section.dates.enumerated()), id: \.offset) { index, date in
                Toggle(isOn: Binding(get: { date.on }, set: { actions.sectionDate(section.id, index, $0) })) {
                    // The pill follows the day and moves under it when the row is narrow or the text large.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: ItmoSpacing.content) { dateLabel(date) }
                        VStack(alignment: .leading, spacing: ItmoSpacing.related) { dateLabel(date) }
                    }
                }
                .disabled(!date.enabled)
                .itmoTint()
                .padding(.horizontal, ItmoKit.padding)
                .padding(.vertical, 11)
                .frame(minHeight: 52)
                .accessibilityIdentifier("sport.section.\(section.id).date.\(index)")
            }
        }
        .accessibilityIdentifier("sport.section.\(section.id)")
    }

    @ViewBuilder
    private func dateLabel(_ date: SportSectionDateUi) -> some View {
        Text(verbatim: date.day.resolved).font(.itmo(.bodyLarge)).fixedSize()
        ItmoStatusPill(pill: ItmoPill(
            text: date.status.resolved,
            tone: date.tone,
            symbol: date.state == .queued ? .hourglassTop : nil
        ))
        .fixedSize()
    }
}

private extension SportSectionDateUi {
    /// The queue's warning tone, as on a booking's queue pill; a spent limit is an error; the rest is quiet.
    var tone: ItmoTone {
        switch state {
        case .queued: .warn
        case .limit: .bad
        default: .neutral
        }
    }
}

/// A secondary line with its small symbol: the slot, the teacher, the hall.
struct SportMetaLine: View {
    let symbol: AppSymbol
    let text: String

    var body: some View {
        Label {
            Text(verbatim: text).font(.itmo(.bodyMedium)).lineLimit(2)
        } icon: {
            symbol.image.font(.itmo(.bodySmall)).accessibilityHidden(true)
        }
        .labelStyle(.titleAndIcon)
        .foregroundStyle(.secondary)
    }
}

/// The score of the semester: the ring with the total, the formula and what is left; the one hero of Mine.
struct SportScoreCard: View {
    let score: SportScoreUi

    var body: some View {
        ItmoCard(tone: score.done ? .ok : .primary, hero: true) {
            HStack(spacing: ItmoSpacing.group) {
                SportScoreRing(score: score, size: 88)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: AppStrings.string("sport_score_title")).font(.itmo(.titleMedium, emphasized: true))
                    Group {
                        Text(verbatim: score.formula.resolved)
                        Text(verbatim: score.status.resolved)
                    }
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                AppSymbol.chevronRight.image.font(.itmo(.bodySmall, emphasized: true)).foregroundStyle(.tertiary)
            }
        }
    }
}

/// Visits and the counted bonus as two arcs out of 100 with the total inside; at 100 one closed ring.
struct SportScoreRing: View {
    let score: SportScoreUi
    var size: CGFloat = 88

    @Environment(\.itmoTrack) private var track

    var body: some View {
        let width = size / 11
        ZStack {
            Circle().stroke(track, lineWidth: width)
            if score.done {
                Circle().stroke(ItmoColor.success, lineWidth: width)
            } else {
                arc(from: 0, to: Double(score.visitsShare), color: ItmoColor.sportScoreAttendance, width: width)
                arc(
                    from: Double(score.visitsShare),
                    to: Double(score.visitsShare + score.bonusShare),
                    color: ItmoColor.sportScoreBonus,
                    width: width
                )
            }
            VStack(spacing: 0) {
                Text(verbatim: "\(score.total)")
                    .font(size >= 110 ? .itmo(.headlineLarge, emphasized: true) : .itmo(.titleLarge, emphasized: true))
                    .monospacedDigit()
                Text(verbatim: AppStrings.string("sport_score_of")).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
            }
            .minimumScaleFactor(0.6)
            .lineLimit(1)
            .padding(width)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func arc(from start: Double, to end: Double, color: Color, width: CGFloat) -> some View {
        Circle()
            .trim(from: min(start, 1), to: min(end, 1))
            .stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round))
            .rotationEffect(.degrees(-90))
    }
}

/// The day over a booking: "Today, 7 Sep".
struct SportDayLabel: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(.itmo(.labelLarge, emphasized: true))
            .foregroundStyle(.secondary)
            .padding(.horizontal, ItmoSpacing.related)
            .padding(.top, ItmoSpacing.related)
    }
}

/// Sign-up: the search, the chips, the week strip that pages by weeks, the day's dense rows with one action.
struct SportSignPart: View {
    let state: SportSignUiState
    let actions: SportContentActions

    var body: some View {
        ItmoSearchField(
            placeholder: AppStrings.string("sport_search_hint"),
            query: Binding(get: { state.query }, set: { actions.query($0) }),
            clearLabel: AppStrings.string("sport_search_clear"),
            action: (.tune, AppStrings.string("sport_filters_title"), Int(state.filterCount), actions.openFilters)
        )
        .accessibilityIdentifier("sport.search")
        ItmoChips(chips: state.chips.map { ItmoChip(label: $0.label.resolved, selected: $0.selected) }) { index in
            actions.chip(state.chips[index].filter)
        }
        SportWeekPager(weeks: state.weeks, selected: Int(state.selectedWeek), actions: actions)
        switch onEnum(of: state.body) {
        case .loading:
            ItmoLoadingView().accessibilityIdentifier("sport.loading")
        case let .failed(failed):
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(failed.error).resolved,
                retry: actions.retrySign
            )
            .accessibilityIdentifier("sport.error")
        case let .day(day):
            if let empty = day.empty {
                ItmoEmptyView(
                    symbol: empty.filtered ? .tune : .eventBusy,
                    title: empty.title.resolved,
                    description: empty.text?.resolved,
                    action: empty.action.map { action in (action.label.resolved, { actions.empty(action) }) }
                )
                .accessibilityIdentifier("sport.empty")
            } else {
                ItmoSectionHeader(title: day.title.resolved, count: day.count?.resolved)
                ItmoListGroup(inset: 72) {
                    ForEach(day.rows, id: \.lessonId) { row in
                        SportCatalogRowView(row: row, open: { actions.openLesson(row.lessonId) }) {
                            actions.rowAction(row.lessonId)
                        }
                    }
                }
                .accessibilityIdentifier("sport.rows")
                if let hidden = day.hidden {
                    HStack {
                        Text(verbatim: hidden.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                        Spacer()
                        Button(action: actions.resetFilters) { Text(verbatim: AppStrings.string("common_reset")) }
                            .font(.itmo(.bodyMedium))
                            .itmoTint()
                    }
                    .padding(.horizontal, ItmoSpacing.related)
                }
            }
        }
    }
}

/// The published weeks of the strip, paging sideways; a page that settles on another week selects a day in it.
struct SportWeekPager: View {
    let weeks: [[SportWeekDay]]
    let selected: Int
    let actions: SportContentActions
    @State private var page: Int?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 0) {
                ForEach(weeks.indices, id: \.self) { index in
                    let week = weeks[index]
                    ItmoWeekStrip(days: week.map(\.kitDay)) { actions.day(week[$0].date) }
                        .containerRelativeFrame(.horizontal)
                        .id(index)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $page)
        .onAppear { page = selected }
        .onChange(of: selected) { _, week in if page != week { withAnimation { page = week } } }
        .onChange(of: page) { _, week in if let week, week != selected { actions.week(week) } }
        .accessibilityIdentifier("sport.strip")
    }
}

extension SportWeekDay {
    var kitDay: ItmoWeekDay {
        ItmoWeekDay(
            id: "\(date)",
            weekday: weekday,
            number: number,
            lessons: Int(lessons),
            today: today,
            past: past,
            selected: selected,
            label: description_.resolved
        )
    }
}

/// One catalog lesson: the time, the section, its kind and place, the places, friends, a clash and one action; at
/// the accessibility sizes the action moves under the lines.
struct SportCatalogRowView: View {
    let row: SportCatalogRow
    let open: () -> Void
    let act: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        HStack(alignment: .center, spacing: ItmoSpacing.content) {
            Button(action: open) { lines.contentShape(.rect) }.buttonStyle(.plain)
            if !typeSize.isAccessibilitySize { SportRowActionView(action: row.action, act: act) }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 10)
        .accessibilityIdentifier("sport.row.\(row.lessonId)")
    }

    private var lines: some View {
        HStack(alignment: .top, spacing: ItmoSpacing.content) {
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: row.start).font(.itmo(.bodyMedium, emphasized: true))
                Text(verbatim: row.end).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
            }
            .monospacedDigit()
            .frame(minWidth: 44, alignment: .leading)
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: row.section).font(.itmo(.bodyLarge, emphasized: true)).lineLimit(2)
                SportKindTag(kind: row.kind.resolved, tone: row.kindTone, place: row.place?.resolved)
                if row.occupancy != nil || !row.friends.isEmpty {
                    HStack(spacing: ItmoSpacing.compact) {
                        if let occupancy = row.occupancy {
                            ItmoOccupancy(text: occupancy.text.resolved, share: Double(occupancy.share), tone: occupancy.tone.kitTone)
                        }
                        SportFriendStack(friends: row.friends)
                    }
                }
                if let clash = row.clash {
                    Label { Text(verbatim: clash.resolved) } icon: { AppSymbol.eventBusy.image }
                        .font(.itmo(.labelMedium, emphasized: true))
                        .foregroundStyle(ItmoTone.warn.color)
                        .lineLimit(1)
                }
                if typeSize.isAccessibilitySize { SportRowActionView(action: row.action, act: act) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Sign up, Queue, a status pill or the lock with MyITMO's reason.
struct SportRowActionView: View {
    let action: SportRowAction
    let act: () -> Void

    var body: some View {
        switch onEnum(of: action) {
        case let .sign(sign):
            button(AppStrings.string("sport_lesson_sign_up"), busy: sign.busy, prominent: true)
        case let .queue(queue):
            button(AppStrings.string("sport_row_queue"), busy: queue.busy, prominent: false)
        case let .status(status):
            ItmoStatusPill(
                pill: ItmoPill(text: status.text.resolved, tone: status.booked ? .ok : .warn, symbol: status.booked ? nil : .hourglassTop)
            )
            .fixedSize()
        case let .blocked(blocked):
            Label { Text(verbatim: blocked.reason.resolved) } icon: { AppSymbol.lock.image }
                .font(.itmo(.labelMedium))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 112, alignment: .trailing)
        }
    }

    private func button(_ title: String, busy: Bool, prominent: Bool) -> some View {
        Button(action: act) {
            ZStack {
                Text(verbatim: title).opacity(busy ? 0 : 1)
                if busy { ProgressView() }
            }
            .font(.itmo(.labelLarge, emphasized: true))
        }
        .buttonStyle(.bordered)
        .tint(prominent ? ItmoColor.primary : .secondary)
        .controlSize(.small)
        .disabled(busy)
        .fixedSize()
        .accessibilityIdentifier("sport.rowAction")
    }
}

/// "• Free, Kronva": the kind's dot and the kind with the place.
struct SportKindTag: View {
    let kind: String?
    let tone: SportKindTone
    var place: String?

    var body: some View {
        let label = [kind, place].compactMap { $0 }.joined(separator: ", ")
        if !label.isEmpty {
            HStack(spacing: 5) {
                Circle().fill(tone.color).frame(width: 7, height: 7).accessibilityHidden(true)
                Text(verbatim: label).lineLimit(1)
            }
            .font(.itmo(.labelMedium))
            .foregroundStyle(.secondary)
        }
    }
}

/// Friends' photos or initials overlapping, at most three.
struct SportFriendStack: View {
    let friends: [UserSummary]

    var body: some View {
        if !friends.isEmpty {
            HStack(spacing: -6) {
                ForEach(friends.prefix(3), id: \.isu) {
                    ItmoAvatar(initials: $0.initials, pictureUrl: $0.pictureUrl, size: 22)
                }
            }
        }
    }
}

extension SportKindTone {
    var color: Color {
        switch self {
        case .free: ItmoColor.lessonTypeSport
        case .section: ItmoColor.lessonTypeLab
        case .team: ItmoColor.lessonTypeAssessment
        case .other: ItmoColor.lessonTypeDefault
        }
    }
}

extension SportSeatsTone {
    var kitTone: ItmoTone {
        switch self {
        case .ok: .ok
        case .few: .warn
        case .full: .bad
        }
    }
}

extension View {
    /// The queue's questions that are alerts: leaving a queue, or why one cannot be joined.
    func sportPromptAlert(_ prompt: SportPrompt?, confirm: @escaping () -> Void, dismiss: @escaping () -> Void) -> some View {
        let leave = prompt as? SportPromptLeave
        let info = prompt as? SportPromptInfo
        let message = leave?.message.resolved ?? info?.message.resolved ?? ""
        return alert(
            Text(verbatim: message),
            isPresented: Binding(get: { leave != nil || info != nil }, set: { if !$0 { dismiss() } })
        ) {
            if leave != nil {
                Button(role: .destructive, action: confirm) { Text(verbatim: AppStrings.string("sport_action_leave_queue")) }
                Button(role: .cancel, action: dismiss) { Text(verbatim: AppStrings.string("common_back")) }
            } else {
                Button(role: .cancel, action: dismiss) { Text(verbatim: AppStrings.string("common_ok")) }
            }
        }
    }

    /// "Cancel the booking?" before a booking or a queue is cancelled.
    func sportCancelAlert(_ prompt: SportCancelPrompt?, confirm: @escaping () -> Void, dismiss: @escaping () -> Void) -> some View {
        alert(
            Text(verbatim: prompt?.message.resolved ?? ""),
            isPresented: Binding(get: { prompt != nil }, set: { if !$0 { dismiss() } })
        ) {
            Button(role: .destructive, action: confirm) { Text(verbatim: prompt?.confirm.resolved ?? "") }
            Button(role: .cancel, action: dismiss) { Text(verbatim: AppStrings.string("common_back")) }
        }
    }
}
