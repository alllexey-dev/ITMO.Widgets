import Shared
import SwiftUI
import UIKit

/// The schedule tab's root (docs/features/schedule.md) over the shared `ScheduleFeedViewModel`: the root header
/// (the month with the month sheet behind it, "today" off the current week, the friend picker), the week strip
/// and the feed. The picker and the month sheet answer through the router (`open(_:onResult:)`); each `today` entry
/// route (the widget, the quick action, the App Shortcut) puts the own schedule back on today.
struct ScheduleScreen: View {
    let router: AppRouter
    @State private var model = ObservableViewModel(state: \.uiState) { store in
        ScheduleFeedModels.make(store, isu: 0, name: "")
    }
    @State private var messages = ScheduleMessages()

    var body: some View {
        ScheduleFeedView(model: model, messages: messages, host: host)
            // A container, as the other tab roots: an identifier on a plain view replaces its header buttons' own.
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("shell.root.schedule")
            .onChange(of: router.todayRequest) { model.viewModel.openToday() }
    }

    private var host: FeedViewHost {
        FeedViewHost(
            lesson: { messages.opened(router.openLesson($0)) },
            month: { iso in
                let route = AppRoutes.ScheduleMonth(date: iso, userIsu: FriendSelectionContract.shared.NO_USER_ISU)
                messages.opened(router.open(route) { (pick: DatePick) in
                    ScheduleIosParametersKt.showScheduleDate(viewModel: model.viewModel, isoDate: pick.iso)
                })
            },
            pickFriend: { shown in
                let route = AppRoutes.FriendSelector(selectedIsu: shown ?? FriendSelectionContract.shared.NO_USER_ISU)
                messages.opened(router.open(route) { (pick: FriendPick) in model.viewModel.selectUser(user: pick.user) })
            }
        )
    }
}

/// Another user's schedule (`AppRoutes.UserSchedule`, from a profile): the same feed pushed on the tab's stack,
/// without the picker; every lesson opens the lesson sheet.
struct UserScheduleScreen: View {
    let isu: Int32
    let name: String
    @Environment(AppRouter.self) private var router
    @State private var model: ObservableViewModel<ScheduleFeedViewModel, ScheduleFeedUiState>
    @State private var messages = ScheduleMessages()

    init(isu: Int32, name: String) {
        self.isu = isu
        self.name = name
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            ScheduleFeedModels.make(store, isu: isu, name: name)
        })
    }

    var body: some View {
        ScheduleFeedView(model: model, messages: messages, host: FeedViewHost(lesson: { messages.opened(router.openLesson($0)) }))
            .accessibilityIdentifier("schedule.user")
    }
}

/// The friend picker's answer to the schedule: the person, or `nil` for the own schedule.
struct FriendPick {
    let user: SelectedUser?
}

/// The month sheet's "open": the day as `2026-09-17`.
struct DatePick {
    let iso: String
}

/// What the feed opens through its host; a pushed feed has no month sheet and no picker.
struct FeedViewHost {
    var lesson: (LessonTarget) -> Void = { _ in }
    /// The month sheet on the ISO day shown now.
    var month: ((String) -> Void)?
    /// The picker, told whose schedule is shown (`nil`: the own).
    var pickFriend: ((Int32?) -> Void)?
}

enum ScheduleFeedModels {
    /// The feed's ViewModel: the tab's own schedule for `isu` 0, else that user's (`scheduleFeedParameters`).
    @MainActor
    static func make(_ store: ScreenViewModelStore, isu: Int32, name: String) -> ScheduleFeedViewModel {
        let parameters: [Any?] = ScheduleIosParametersKt.scheduleFeedParameters(userIsu: isu, name: name)
        guard let model = store.resolve(type: ScheduleFeedViewModel.self, parameters: parameters) as? ScheduleFeedViewModel
        else {
            preconditionFailure("Koin resolved no ScheduleFeedViewModel")
        }
        return model
    }
}

extension AppRouter {
    /// A lesson of any list opens its sheet: a timetable lesson or an own sport queue.
    func openLesson(_ target: LessonTarget) -> RouteOpening {
        switch onEnum(of: target) {
        case let .lesson(lesson): open(AppRoutes.LessonDetails(args: lesson.args))
        case let .pendingSport(sport): open(AppRoutes.PendingSportDetails(args: sport.args))
        }
    }
}

/// The feed with its ViewModel under the root header; a pushed screen keeps the stack's navigation bar instead.
private struct ScheduleFeedView: View {
    let model: ObservableViewModel<ScheduleFeedViewModel, ScheduleFeedUiState>
    let messages: ScheduleMessages
    let host: FeedViewHost

    var body: some View {
        let state = model.state
        let content = ScheduleFeedContent(state: state, actions: actions)
            .overlay(alignment: .bottom) {
                if state.friend != nil {
                    ItmoProminentButton(title: AppStrings.string("schedule_return_to_mine"), symbol: .undo) {
                        model.viewModel.selectUser(user: nil)
                    }
                    .fixedSize()
                    .padding(.bottom, ItmoSpacing.content)
                    .accessibilityIdentifier("schedule.backToMine")
                }
            }
            .refreshable {
                await model.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                    $0.uiState.value.refreshing
                })
            }
            .observing(model)
            .onEvents(of: model, \.events) { event in
                switch onEnum(of: event) {
                case let .showError(error): messages.show(AppErrorTextsKt.toUiText(error.error).resolved)
                }
            }
            .onAppear { model.viewModel.refresh(mode: .silent) }
            .scheduleMessages(messages)
        if host.pickFriend == nil {
            content
                .navigationTitle(Text(verbatim: state.title.resolved))
                .navigationSubtitle(Text(verbatim: state.subtitle?.resolved ?? ""))
                .toolbar {
                    if state.offWeek {
                        ToolbarItem(placement: .topBarTrailing) { todayButton }
                    }
                }
        } else {
            content
                .itmoRootHeader(
                    title: state.title.resolved,
                    subtitle: state.subtitle?.resolved,
                    titleMenu: state.monthMenu ? { host.month?("\(state.selected)") } : nil,
                    titleMenuLabel: AppStrings.string("schedule_month_action")
                ) {
                    if state.offWeek { todayButton }
                    if state.canPickFriend {
                        Button { host.pickFriend?(state.friend?.isu) } label: {
                            AppSymbol.group.image.frame(width: 28, height: 28)
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.circle)
                        .accessibilityLabel(Text(verbatim: AppStrings.string("schedule_friends_action")))
                        .accessibilityIdentifier("schedule.friends")
                    }
                }
        }
    }

    private var todayButton: some View {
        Button { model.viewModel.showToday() } label: {
            AppSymbol.arrowUpward.image.frame(width: 28, height: 28)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel(Text(verbatim: AppStrings.string("schedule_today")))
        .accessibilityIdentifier("schedule.today")
    }

    private var actions: FeedViewActions {
        let viewModel = model.viewModel
        return FeedViewActions(
            lesson: host.lesson,
            visible: { viewModel.onVisibleDate(date: $0) },
            nearStart: { viewModel.onNearStart() },
            nearEnd: { viewModel.onNearEnd() },
            day: { viewModel.showDate(date: $0) },
            week: { viewModel.showWeek(monday: $0) },
            pagingStarted: { viewModel.beginWeekPaging() },
            jumped: { viewModel.onJumped(id: $0) },
            retry: { viewModel.refresh(mode: .force) }
        )
    }
}

/// What the feed asks its host to do. Dates are the state's own `LocalDate`s.
struct FeedViewActions {
    var lesson: (LessonTarget) -> Void = { _ in }
    var visible: (Kotlinx_datetimeLocalDate) -> Void = { _ in }
    var nearStart: () -> Void = {}
    var nearEnd: () -> Void = {}
    var day: (Kotlinx_datetimeLocalDate) -> Void = { _ in }
    var week: (Kotlinx_datetimeLocalDate) -> Void = { _ in }
    var pagingStarted: () -> Void = {}
    var jumped: (Int32) -> Void = { _ in }
    var retry: () -> Void = {}
}

/// The week strip and the feed without the ViewModel: sticky day headers over the kit's timeline, short "no lessons"
/// rows, collapsed weekends, week labels; loading and the error with retry.
struct ScheduleFeedContent: View {
    let state: ScheduleFeedUiState
    var actions = FeedViewActions()

    var body: some View {
        VStack(spacing: 0) {
            weekPager
            Divider()
            switch onEnum(of: state.body) {
            case .loading:
                ItmoLoadingView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityIdentifier("schedule.loading")
            case let .failed(failed):
                ScrollView {
                    ItmoErrorView(
                        title: AppStrings.string("common_load_error_title"),
                        description: AppErrorTextsKt.toUiText(failed.error).resolved,
                        retry: actions.retry
                    )
                    .padding(.top, ItmoSpacing.section)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityIdentifier("schedule.error")
            case let .days(days):
                ScheduleFeedList(state: state, days: days, actions: actions)
            }
        }
        .background(Color(.systemGroupedBackground))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("schedule.feed")
    }

    private var weekPager: some View {
        ScheduleWeekPager(state: state, actions: actions)
    }

}

/// The lazy layout and scrollPosition use exactly the same String ID, not a bridged Kotlin date object.
private struct ScheduleWeekPage: Identifiable {
    let week: StripWeek
    var id: String { "\(week.monday)" }
}

/// Stable Monday IDs and scroll phases keep programmatic selection and user paging from feeding each other.
struct ScheduleWeekPager: View {
    let state: ScheduleFeedUiState
    let actions: FeedViewActions
    @State private var page: String?
    @State private var userPaging = false
    @State private var scrollIdentity = 0
    @State private var seenJump: Int32?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let height: CGFloat = 80

    private var selectedMonday: String {
        "\(ScheduleFeedPresenter.shared.monday(date: state.selected))"
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 0) {
                ForEach(state.weeks.map { ScheduleWeekPage(week: $0) }) { item in
                    let week = item.week
                    ItmoWeekStrip(
                        days: week.days.map {
                            ItmoWeekDay(
                                id: "\($0.date)", weekday: $0.weekday, number: $0.number, lessons: Int($0.lessons),
                                changed: $0.changed, today: $0.today, past: $0.past,
                                selected: "\($0.date)" == "\(state.selected)", label: "\($0.weekday) \($0.number)"
                            )
                        },
                        tap: { actions.day(week.days[$0].date) }
                    )
                    .padding(.horizontal, ItmoSpacing.related)
                    .containerRelativeFrame(.horizontal)
                    .frame(height: Self.height)
                    .id(item.id)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $page)
        .id(scrollIdentity)
        .onChange(of: "\(selectedMonday):\(state.jump?.id ?? -1)", initial: true) { _, _ in
            let target = selectedMonday
            let explicit = state.jump.map { $0.id != seenJump } ?? false
            if let jump = state.jump { seenJump = jump.id }
            let interrupted = explicit && userPaging
            guard !userPaging || explicit, page != target || interrupted else { return }
            if explicit { userPaging = false }
            if interrupted {
                // Recreate only an interrupted scroll: assigning its current ID would not cancel its fling.
                scrollIdentity += 1
            }
            if page == nil || reduceMotion || interrupted {
                page = target
                return
            }
            withAnimation(.smooth(duration: 0.25)) { page = target }
        }
        .onScrollPhaseChange { _, phase in
            switch phase {
            case .tracking, .interacting:
                if !userPaging { actions.pagingStarted() }
                userPaging = true
            case .idle:
                guard userPaging else { return }
                userPaging = false
                guard let page, page != selectedMonday,
                      let week = state.weeks.first(where: { "\($0.monday)" == page }) else { return }
                actions.week(week.monday)
            default: break
            }
        }
        .accessibilityScrollAction { edge in
            let current = state.weeks.firstIndex { "\($0.monday)" == selectedMonday } ?? Int(state.selectedWeek)
            let next = current + ((edge == .leading || edge == .top) ? -1 : 1)
            guard state.weeks.indices.contains(next) else { return }
            actions.pagingStarted()
            actions.week(state.weeks[next].monday)
        }
        .frame(height: Self.height)
        // A scroll view that touches the top bar's edge takes the bar's height as a vertical content inset, and this
        // 80-point one then draws its days below its own clip while they still take taps: keep it off that edge.
        .padding(.top, ItmoSpacing.related)
        .accessibilityIdentifier("schedule.strip")
    }
}

/// One item of the feed's list: a day with lessons is a section whose header sticks.
private enum FeedItem: Identifiable {
    case label(FeedRowWeekLabel)
    case day(FeedRowDayHeader, FeedRowLessons)
    case quiet(id: String, date: Kotlinx_datetimeLocalDate, symbol: AppSymbol, text: String)

    var id: String {
        switch self {
        case let .label(row): row.key
        case let .day(header, _): header.key
        case let .quiet(id, _, _, _): id
        }
    }

    var date: Kotlinx_datetimeLocalDate {
        switch self {
        case let .label(row): row.date
        case let .day(header, _): header.date
        case let .quiet(_, date, _, _): date
        }
    }

    static func of(_ rows: [FeedRow]) -> [FeedItem] {
        var items: [FeedItem] = []
        var index = 0
        while index < rows.count {
            switch onEnum(of: rows[index]) {
            case let .weekLabel(row): items.append(.label(row))
            case let .dayHeader(header):
                if index + 1 < rows.count, let lessons = rows[index + 1] as? FeedRowLessons {
                    items.append(.day(header, lessons))
                    index += 1
                }
            case .lessons: break
            case let .noLessons(row):
                items.append(.quiet(id: row.key, date: row.date, symbol: .eventAvailable, text: row.text.resolved))
            case let .weekend(row):
                items.append(.quiet(id: row.key, date: row.date, symbol: .weekend, text: row.text.resolved))
            }
            index += 1
        }
        return items
    }
}

private struct ScheduleFeedList: View {
    let state: ScheduleFeedUiState
    let days: ScheduleFeedBodyDays
    let actions: FeedViewActions
    @State private var top: String?

    var body: some View {
        let items = FeedItem.of(days.rows)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    ForEach(items) { item in
                        switch item {
                        case let .label(row):
                            weekLabel(row.text.resolved).id(item.id).accessibilityIdentifier("schedule.row.\(item.id)")
                        case let .day(header, lessons):
                            Section {
                                ItmoLessonTimeline(rows: lessons.items.kitRows, showsNow: lessons.showNow) { index in
                                    if let lesson = lessons.items[index] as? TimelineItemLesson {
                                        actions.lesson(lesson.lesson.target)
                                    }
                                }
                                .padding(.horizontal, ItmoKit.margin)
                                .padding(.bottom, ItmoSpacing.related)
                                .accessibilityElement(children: .contain)
                                .accessibilityIdentifier("schedule.day.\(header.date)")
                            } header: {
                                dayHeader(header).accessibilityIdentifier("schedule.row.\(item.id)")
                            }
                            .id(item.id)
                        case let .quiet(_, _, symbol, text):
                            quietRow(symbol: symbol, text: text).id(item.id).accessibilityIdentifier("schedule.row.\(item.id)")
                        }
                    }
                }
                .scrollTargetLayout()
                .padding(.bottom, 88)
            }
            .accessibilityIdentifier("schedule.list")
            .scrollPosition(id: $top, anchor: .top)
            .onChange(of: top) { _, id in
                guard state.jump == nil, let id, let item = items.first(where: { $0.id == id }) else { return }
                actions.visible(item.date)
            }
            .onScrollGeometryChange(for: Edge?.self) { geometry in
                if geometry.contentOffset.y + geometry.containerSize.height > geometry.contentSize.height - 400 {
                    return .bottom
                }
                return geometry.contentOffset.y < 40 ? .top : nil
            } action: { _, edge in
                guard state.jump == nil else { return }
                switch edge {
                case .bottom: actions.nearEnd()
                case .top: actions.nearStart()
                default: break
                }
            }
            .task(id: "\(state.jump?.id ?? -1)-\(days.rows.count)-\(state.loading)") {
                // Before the first load the feed has no rows yet: keep the jump (today on launch) for the rows to come.
                guard !Task.isCancelled, let jump = state.jump, !days.rows.isEmpty else { return }
                // While the day's week loads its row may still arrive; jump to it then.
                let exact = items.contains { "\($0.date)" == "\(jump.date)" }
                if !exact && state.loading { return }
                let index = Int(days.indexOf(date: jump.date))
                if index >= 0, let target = items.first(where: { "\($0.date)" == "\(days.rows[index].date)" }) {
                    await ScheduleFeedScroll.settle(
                        scroll: {
                            // The scroll position follows the jump, so rows that load above it later keep it in place.
                            top = target.id
                            proxy.scrollTo(target.id, anchor: .top)
                        },
                        completed: { actions.jumped(jump.id) }
                    )
                    return
                }
                guard !Task.isCancelled else { return }
                actions.jumped(jump.id)
            }
        }
    }

    private func weekLabel(_ text: String) -> some View {
        HStack(spacing: ItmoSpacing.content) {
            Text(verbatim: text).font(.itmo(.labelLarge, emphasized: true)).foregroundStyle(ItmoColor.primary)
            VStack { Divider() }
        }
        .padding(.horizontal, ItmoKit.margin)
        .padding(.top, ItmoSpacing.group)
        .padding(.bottom, ItmoSpacing.related)
    }

    private func dayHeader(_ header: FeedRowDayHeader) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.compact) {
            Text(verbatim: header.title)
                .font(.itmo(.titleMedium, emphasized: true))
                .foregroundStyle(header.today ? ItmoColor.primary : Color.primary)
                .layoutPriority(1)
                .accessibilityAddTraits(.isHeader)
            if header.today {
                ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("schedule_today_pill"), tone: .primary))
                    .fixedSize()
            }
            Spacer(minLength: ItmoSpacing.compact)
            Text(verbatim: header.count.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary).monospacedDigit()
                .fixedSize()
        }
        // A past day reads as the strip's past days; the opaque background stays under the sticky header.
        .opacity(header.past ? 0.65 : 1)
        .padding(.horizontal, ItmoKit.margin)
        .padding(.top, ItmoSpacing.content)
        .padding(.bottom, ItmoSpacing.compact)
        .background(Color(.systemGroupedBackground))
    }

    private func quietRow(symbol: AppSymbol, text: String) -> some View {
        HStack(spacing: ItmoSpacing.content) {
            symbol.image.foregroundStyle(.secondary).accessibilityHidden(true)
            Text(verbatim: text).font(.itmo(.bodyLarge)).foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, ItmoSpacing.content)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: ItmoKit.cardCorner))
        .padding(.horizontal, ItmoKit.margin)
        .padding(.vertical, ItmoSpacing.compact)
    }
}

/// What the schedule's screens say beside their content: a key the demo refuses, an error behind content.
@MainActor
@Observable
final class ScheduleMessages {
    var message: SettingsMessage?

    /// The answer of the router to a row or button of the content.
    func opened(_ opening: RouteOpening) {
        guard opening == .refusedInDemo else { return }
        say("error_demo_unavailable")
    }

    /// The catalog string `key`, read aloud too (`show(_:)` for a resolved text).
    func say(_ key: String) {
        show(AppStrings.string(key))
    }
}

extension View {
    /// The message of `messages` at the bottom for a few seconds.
    func scheduleMessages(_ messages: ScheduleMessages) -> some View {
        overlay(alignment: .bottom) {
            if let message = messages.message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                        if messages.message?.id == message.id { messages.message = nil }
                    }
            }
        }
        .animation(.default, value: messages.message?.id)
    }
}

extension ScheduleMessages {
    /// A message whose text the shared content resolved (a failed save, a sheet's note), read aloud too.
    func show(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}
