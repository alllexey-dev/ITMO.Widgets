import Shared
import SwiftUI
import UIKit

/// The lesson sheet (`AppRoutes.LessonDetails` or `AppRoutes.PendingSportDetails`, from any lesson list) over the shared
/// `LessonSheetViewModel`, one per opening: the meeting opens in its app or Safari; the subject, its links, a person
/// and the sport tab open in the sheet's place.
struct LessonSheet: View {
    let route: any AppRoute
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<LessonSheetViewModel, LessonSheetUiState>
    @State private var messages = ScheduleMessages()

    init(route: any AppRoute) {
        self.route = route
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = ScheduleIosParametersKt.lessonSheetParameters(route: route)
            guard let model = store.resolve(type: LessonSheetViewModel.self, parameters: parameters) as? LessonSheetViewModel
            else {
                preconditionFailure("Koin resolved no LessonSheetViewModel")
            }
            return model
        })
    }

    var body: some View {
        // The full height from the first frame, as on Android: the friends arriving later never resize the sheet.
        ItmoSheet(title: model.state.over.resolved, height: .full) {
            LessonSheetContent(
                state: model.state,
                actions: LessonSheetViewActions(
                    video: { url in
                        guard let link = URL(string: url) else { return }
                        UIApplication.shared.open(link) { opened in
                            if !opened { messages.say("link_open_failed") }
                        }
                    },
                    route: { route in
                        dismiss()
                        messages.opened(router.open(route))
                    },
                    profile: { isu in
                        dismiss()
                        router.open(AppRoutes.UserProfile(isu: isu))
                    },
                    sport: {
                        dismiss()
                        router.selectedTab = .sport
                    }
                )
            )
        }
        .observing(model)
        .scheduleMessages(messages)
        .accessibilityIdentifier("schedule.lessonSheet")
    }
}

/// What the lesson sheet asks its host to do.
struct LessonSheetViewActions {
    var video: (String) -> Void = { _ in }
    var route: (any AppRoute) -> Void = { _ in }
    var profile: (Int32) -> Void = { _ in }
    var sport: () -> Void = {}
}

/// The lesson sheet without the ViewModel: the status and the subject, the big room (or online) with the time and
/// the address, the relevant actions, the change, the note, the teacher, the flow, the sport row and the friends.
/// While the actions may still change their row keeps its height, empty, so nothing below moves when they come.
struct LessonSheetContent: View {
    let state: LessonSheetUiState
    var actions = LessonSheetViewActions()

    var body: some View {
        ScrollViewReader { proxy in
            VStack(alignment: .leading, spacing: ItmoKit.gap) {
                hero
                if state.actionsPending {
                    tile(LessonSheetContent.placeholder, first: true) {}
                        .hidden()
                        .accessibilityHidden(true)
                } else if !state.actions.isEmpty {
                    HStack(alignment: .top, spacing: ItmoSpacing.related) {
                        ForEach(state.actions.indices, id: \.self) { index in
                            tile(state.actions[index], first: index == 0) {
                                let action = state.actions[index]
                                switch action.kind {
                                case .video: if let url = action.url { actions.video(url) }
                                case .friends: withAnimation { proxy.scrollTo("friends", anchor: .top) }
                                case .route, .links, .subject: if let route = action.route { actions.route(route) }
                                }
                            }
                        }
                    }
                }
                if let changes = state.changes { changesGroup(changes) }
                details
                if !state.friends.isEmpty {
                    ItmoSectionHeader(title: AppStrings.string("schedule_sheet_friends_title"), count: "\(state.friends.count)")
                        .id("friends")
                    ItmoListGroup(inset: 68) {
                        ForEach(state.friends, id: \.isu) { friend in
                            ItmoRow(title: friend.name, subtitle: friend.groups.first?.name, chevron: true, action: {
                                actions.profile(friend.isu)
                            }) {
                                ItmoAvatar(initials: friend.initials, pictureUrl: friend.pictureUrl)
                            }
                        }
                    }
                }
            }
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            if let status = state.status { ItmoStatusPill(pill: status.kitPill) }
            Text(verbatim: state.subject).font(.itmo(.headlineSmall, emphasized: true))
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .lastTextBaseline, spacing: ItmoSpacing.content) { place }
                VStack(alignment: .leading, spacing: ItmoSpacing.related) { place }
            }
        }
    }

    @ViewBuilder private var place: some View {
        if let big = state.online ? AppStrings.string("schedule_sheet_online") : state.room?.resolved {
            Text(verbatim: big)
                .font(.itmo(.displayMedium, emphasized: true))
                .foregroundStyle(ItmoColor.primary)
                .accessibilityIdentifier("lessonSheet.room")
        }
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: state.time.resolved).font(.itmo(.titleSmall, emphasized: true)).monospacedDigit()
            if let address = state.address {
                Text(verbatim: address).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
        }
    }

    /// What the empty row measures while the actions may still change.
    private static let placeholder = LessonAction(
        kind: .route, label: UiTextDynamic(value: " "), url: nil, route: nil, enabled: false
    )

    private func tile(_ action: LessonAction, first: Bool, run: @escaping () -> Void) -> some View {
        Button(action: run) {
            VStack(spacing: ItmoSpacing.compact) {
                symbol(action.kind).image
                    .font(.itmo(.titleLarge))
                    .frame(maxWidth: .infinity, minHeight: 52)
                    // The rest in the primary container (Android's tonal button): a grey tile reads as disabled.
                    .foregroundStyle(tileForeground(action, first: first))
                    .background(tileBackground(action, first: first), in: .rect(cornerRadius: 18))
                Text(verbatim: action.label.resolved)
                    .font(.itmo(.labelMedium))
                    .lineLimit(1)
                    .foregroundStyle(action.enabled ? HierarchicalShapeStyle.primary : HierarchicalShapeStyle.tertiary)
            }
        }
        .buttonStyle(.plain)
        .disabled(!action.enabled)
        .accessibilityIdentifier("lessonSheet.action.\(action.kind.name.lowercased())")
    }

    /// The friends tile while they load or with nobody on the lesson: the grey of a disabled control.
    private func tileForeground(_ action: LessonAction, first: Bool) -> Color {
        if !action.enabled { return Color(.tertiaryLabel) }
        return first ? ItmoColor.onPrimary : ItmoColor.onPrimaryContainer
    }

    private func tileBackground(_ action: LessonAction, first: Bool) -> Color {
        if !action.enabled { return Color(.tertiarySystemFill) }
        return first ? ItmoColor.primary : ItmoColor.primaryContainer
    }

    private func symbol(_ kind: LessonActionKind) -> AppSymbol {
        switch kind {
        case .route: .directions
        case .video: .videocam
        case .friends: .group
        case .links: .link
        case .subject: .menuBook
        }
    }

    private func changesGroup(_ changes: LessonSheetChanges) -> some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            ItmoSectionHeader(title: AppStrings.string("schedule_sheet_changes"))
            ItmoListGroup(inset: ItmoKit.padding) {
                if let summary = changes.summary {
                    ItmoRow(title: summary.resolved)
                }
                ForEach(changes.lines.indices, id: \.self) { index in
                    let line = changes.lines[index]
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: line.field.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                        ItmoFlow(spacing: 6) {
                            Text(verbatim: line.from.resolved).strikethrough().foregroundStyle(.secondary)
                            AppSymbol.arrowForward.image.foregroundStyle(.secondary).accessibilityHidden(true)
                            Text(verbatim: line.to.resolved).fontWeight(.semibold).foregroundStyle(ItmoTone.warn.color)
                        }
                        .font(.itmo(.bodyLarge))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(ItmoKit.padding)
                }
            }
            ItmoGroupNote(text: changes.note.resolved)
        }
    }

    @ViewBuilder private var details: some View {
        if state.note != nil || state.password != nil || state.teacher != nil || state.flow != nil || state.sport != nil {
            ItmoListGroup(inset: 68) {
                if let note = state.note {
                    ItmoRow(title: note, subtitle: AppStrings.string("schedule_sheet_note"), symbol: .editNote)
                }
                if let password = state.password {
                    ItmoRow(title: AppStrings.string("schedule_sheet_password", [password]), symbol: .videocam)
                }
                if let teacher = state.teacher {
                    ItmoRow(
                        title: teacher.name,
                        subtitle: teacher.tone.resolved,
                        chevron: teacher.isu != nil,
                        action: teacher.isu.map { isu in { actions.profile(isu.int32Value) } }
                    ) {
                        ItmoAvatar(initials: UserNamesKt.personInitials(name: teacher.name))
                    }
                    .accessibilityIdentifier("lessonSheet.teacher")
                }
                if let flow = state.flow {
                    ItmoRow(title: flow, subtitle: AppStrings.string("schedule_lesson_details_flow"), symbol: .group)
                }
                if let sport = state.sport {
                    ItmoRow(
                        title: sport.title.resolved, subtitle: sport.text.resolved, symbol: .sprint, tone: .warn,
                        chevron: true, action: actions.sport
                    )
                    .accessibilityIdentifier("lessonSheet.sport")
                }
            }
        }
    }
}

/// The month sheet (`AppRoutes.ScheduleMonth`) over the shared `ScheduleMonthViewModel`: the month with a dot per
/// lesson and "open", which answers the feed through the router and closes the sheet.
struct ScheduleMonthSheet: View {
    let route: AppRoutes.ScheduleMonth
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<ScheduleMonthViewModel, ScheduleMonthUiState>

    init(route: AppRoutes.ScheduleMonth) {
        self.route = route
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = ScheduleIosParametersKt.scheduleMonthParameters(route: route)
            guard let model = store.resolve(type: ScheduleMonthViewModel.self, parameters: parameters) as? ScheduleMonthViewModel
            else {
                preconditionFailure("Koin resolved no ScheduleMonthViewModel")
            }
            return model
        })
    }

    var body: some View {
        ItmoSheet(title: AppStrings.string("schedule_month_title"), height: .content) {
            ScheduleMonthContent(
                state: model.state,
                actions: MonthSheetViewActions(
                    previous: { model.viewModel.previousMonth() },
                    next: { model.viewModel.nextMonth() },
                    select: { model.viewModel.select(date: $0) },
                    open: { iso in
                        router.deliver(DatePick(iso: iso), from: route)
                        dismiss()
                    }
                )
            )
        }
        .observing(model)
        .accessibilityIdentifier("schedule.month")
    }
}

/// What the month sheet asks its host to do.
struct MonthSheetViewActions {
    var previous: () -> Void = {}
    var next: () -> Void = {}
    var select: (Kotlinx_datetimeLocalDate) -> Void = { _ in }
    var open: (String) -> Void = { _ in }
}

/// The month without the ViewModel: the title with the month arrows, the weekday names, the days with their dots,
/// today ringed and the chosen day filled, and one "open".
struct ScheduleMonthContent: View {
    let state: ScheduleMonthUiState
    var actions = MonthSheetViewActions()

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoKit.gap) {
            HStack {
                Text(verbatim: state.title).font(.itmo(.titleMedium, emphasized: true))
                Spacer()
                Button(action: actions.previous) { AppSymbol.chevronLeft.image.frame(width: 28, height: 28) }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("schedule_month_previous")))
                Button(action: actions.next) { AppSymbol.chevronRight.image.frame(width: 28, height: 28) }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("schedule_month_next")))
            }
            let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)
            LazyVGrid(columns: columns, spacing: 2) {
                ForEach(state.weekdays, id: \.self) { weekday in
                    Text(verbatim: weekday).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
                }
                ForEach(0..<Int(state.leading), id: \.self) { _ in Color.clear.frame(height: 1) }
                ForEach(state.days, id: \.number) { day in cell(day) }
            }
            ItmoProminentButton(title: state.open.resolved) { actions.open("\(state.selected)") }
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("schedule.month.open")
        }
    }

    private func cell(_ day: MonthDay) -> some View {
        Button { actions.select(day.date) } label: {
            VStack(spacing: 2) {
                Text(verbatim: day.number)
                    .font(.itmo(.bodyLarge, emphasized: day.today || day.selected))
                    .monospacedDigit()
                    .foregroundStyle(day.selected ? .white : day.today ? ItmoColor.primary : day.past ? .secondary : .primary)
                    .frame(width: 36, height: 36)
                    .background(day.selected ? ItmoColor.primary : .clear, in: .circle)
                    .overlay {
                        if day.today && !day.selected { Circle().stroke(ItmoColor.primary, lineWidth: 1.5) }
                    }
                HStack(spacing: 2) {
                    ForEach(0..<Int(day.lessons), id: \.self) { _ in
                        Circle().fill(ItmoColor.primary.opacity(day.past ? 0.35 : 0.7)).frame(width: 4, height: 4)
                    }
                }
                .frame(height: 4)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(day.selected ? .isSelected : [])
    }
}

/// The friend picker (`AppRoutes.FriendSelector`) over the shared `FriendPickerViewModel`: one tap answers the feed
/// through the router and closes the sheet; without the connection the gate opens its settings.
struct FriendPickerSheet: View {
    let route: AppRoutes.FriendSelector
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<FriendPickerViewModel, FriendPickerUiState>

    init(route: AppRoutes.FriendSelector) {
        self.route = route
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = ScheduleIosParametersKt.friendPickerParameters(route: route)
            guard let model = store.resolve(type: FriendPickerViewModel.self, parameters: parameters) as? FriendPickerViewModel
            else {
                preconditionFailure("Koin resolved no FriendPickerViewModel")
            }
            return model
        })
    }

    var body: some View {
        ItmoSheet(title: AppStrings.string("schedule_picker_title"), height: .content) {
            FriendPickerContent(
                state: model.state,
                actions: PickerViewActions(
                    pick: { user in
                        router.deliver(FriendPick(user: user), from: route)
                        dismiss()
                    },
                    connect: {
                        dismiss()
                        router.open(AppRoutes.Settings(page: "SERVICES"))
                    },
                    retry: { model.viewModel.retry() }
                )
            )
        }
        .observing(model)
        .accessibilityIdentifier("schedule.friendPicker")
    }
}

/// What the picker asks its host to do.
struct PickerViewActions {
    var pick: (SelectedUser?) -> Void = { _ in }
    var connect: () -> Void = {}
    var retry: () -> Void = {}
}

/// The picker without the ViewModel: the own schedule and the friends with where they are now; a friend who hid the
/// schedule is dimmed and cannot be picked; the gate, the empty list and the error.
struct FriendPickerContent: View {
    let state: FriendPickerUiState
    var actions = PickerViewActions()

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoKit.gap) {
            let rows = (state.friends as? PickerFriendsContent)?.rows ?? []
            VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
                ItmoListGroup(inset: 68) {
                    person(state.own).accessibilityIdentifier("friendPicker.own")
                    ForEach(rows.indices, id: \.self) { index in
                        person(rows[index]).accessibilityIdentifier("friendPicker.friend.\(rows[index].user?.isu ?? 0)")
                    }
                }
                if !rows.isEmpty { ItmoGroupNote(text: AppStrings.string("schedule_picker_note")) }
            }
            switch onEnum(of: state.friends) {
            case .loading:
                ItmoLoadingView()
            case .disabled:
                ItmoGateCard(
                    title: AppStrings.string("schedule_picker_gate_title"),
                    reason: AppStrings.string("schedule_picker_gate_reason"),
                    fix: (AppStrings.string("schedule_picker_gate_fix"), actions.connect)
                )
                .accessibilityIdentifier("friendPicker.gate")
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(failed.error).resolved,
                    retry: actions.retry
                )
            case let .content(content):
                if content.rows.isEmpty {
                    ItmoEmptyView(
                        symbol: .group,
                        title: AppStrings.string("schedule_picker_empty"),
                        description: AppStrings.string("schedule_picker_note")
                    )
                    .accessibilityIdentifier("friendPicker.empty")
                }
            }
        }
    }

    private func person(_ row: PickerRow) -> some View {
        ItmoRow(
            title: row.title.resolved,
            subtitle: row.status?.resolved,
            action: row.selectable ? { actions.pick(row.user) } : nil,
            leading: { ItmoAvatar(initials: row.initials, pictureUrl: row.avatar, tone: row.user == nil ? .primary : .tertiary) },
            accessory: {
                if row.selected {
                    AppSymbol.checkCircle.image.foregroundStyle(ItmoColor.primary).accessibilityHidden(true)
                }
            }
        )
        .opacity(row.selectable ? 1 : 0.5)
        .accessibilityAddTraits(row.selected ? .isSelected : [])
    }
}
