import Shared
import SwiftUI
import UIKit

/// Today (docs/features/home.md) over the shared `TodayViewModel`: the hero with the lesson to go to now, the day
/// as the kit's timeline (tomorrow in the evening) and the things to do when there is something, under the root
/// header of `TodayHeaderViewModel` (the date, the bell with the count of unseen events); the header stays static
/// while the list scrolls under it. A key the demo refuses says `error_demo_unavailable`, as Android's shell does.
struct TodayScreen: View {
    let router: AppRouter
    @State private var header = ObservableViewModel(TodayHeaderViewModel.self, state: \.uiState)
    @State private var model = ObservableViewModel(TodayViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?

    var body: some View {
        TodayContent(
            state: model.state,
            actions: TodayContentActions(
                lesson: { open(target: $0) },
                join: { url in URL(string: url).map { UIApplication.shared.open($0) } },
                route: { opened(router.open($0)) },
                todo: { router.open(entry: $0) },
                retry: { model.viewModel.refresh(mode: .force) }
            )
        )
        .refreshable { model.viewModel.refresh(mode: .pull) }
        .itmoRootHeader(title: header.state.title.resolved, subtitle: header.state.subtitle.resolved) {
            ItmoBellButton(label: AppStrings.string("today_notifications"), badge: Int(header.state.badge)) {
                opened(router.open(AppRoutes.Bell.shared))
            }
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
        .animation(.default, value: message?.id)
        .observing(header)
        .observing(model)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.home")
        .onAppear {
            model.viewModel.ensureDataLoaded()
            #if DEBUG
            NotificationFixtures.postIfRequested()
            #endif
        }
    }

    private func open(target: LessonTarget) {
        switch onEnum(of: target) {
        case let .lesson(lesson): opened(router.open(AppRoutes.LessonDetails(args: lesson.args)))
        case let .pendingSport(sport): opened(router.open(AppRoutes.PendingSportDetails(args: sport.args)))
        }
    }

    private func opened(_ opening: RouteOpening) {
        guard opening == .refusedInDemo else { return }
        let text = AppStrings.string("error_demo_unavailable")
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}

/// What Today asks its host to do.
struct TodayContentActions {
    var lesson: (LessonTarget) -> Void = { _ in }
    var join: (String) -> Void = { _ in }
    var route: (any AppRoute) -> Void = { _ in }
    var todo: (EntryRoute) -> Void = { _ in }
    var retry: () -> Void = {}
}

/// Today's list without the ViewModel: loading, the error with retry, the hero, the day (dimmed under
/// "data as of 11:44" after a failed refresh), the things to do, or "no lessons today and tomorrow".
struct TodayContent: View {
    let state: TodayUiState
    var actions = TodayContentActions()

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state) {
            case .loading:
                ItmoLoadingView()
                    .padding(.top, ItmoSpacing.section)
                    .accessibilityIdentifier("today.loading")
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(failed.error).resolved,
                    retry: actions.retry
                )
                .accessibilityIdentifier("today.error")
            case let .content(content):
                body(of: content)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("today.list")
    }

    @ViewBuilder
    private func body(of content: TodayUiStateContent) -> some View {
        if let hero = content.hero {
            TodayHeroCard(hero: hero, actions: actions)
        }
        if let day = content.day {
            ItmoSectionHeader(title: day.title.resolved, count: day.count.resolved)
            if let stale = content.stale {
                ItmoStale(since: stale.resolved) { timeline(day) }
            } else {
                timeline(day)
            }
        } else if content.hero == nil {
            ItmoEmptyView(symbol: .today, title: AppStrings.string("today_empty_title"))
                .accessibilityIdentifier("today.empty")
        }
        if !content.todos.isEmpty {
            ItmoSectionHeader(title: AppStrings.string("today_todo_title"))
            ForEach(content.todos, id: \.id) { todo in
                ItmoActionCard(
                    title: todo.title.resolved,
                    reason: todo.reason.resolved,
                    action: (todo.action.resolved, { actions.todo(todo.target) }),
                    symbol: todo.icon.symbol,
                    tone: .warn
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("today.todo.\(todo.id)")
            }
        }
    }

    private func timeline(_ day: TodayDay) -> some View {
        ItmoLessonTimeline(rows: day.items.kitRows, showsNow: day.showNow) { index in
            if let lesson = day.items[index] as? TimelineItemLesson { actions.lesson(lesson.lesson.target) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("today.day")
    }
}

/// The flow's one hero: where to go (the big room and the building in full) or "online" with a small "join", how long
/// until it starts or ends, what it is; the current lesson's elapsed part. The way there ("route") waits for the
/// map.
struct TodayHeroCard: View {
    let hero: TodayHero
    var actions = TodayContentActions()

    var body: some View {
        Button { actions.lesson(hero.target) } label: { card.contentShape(.rect) }
            .buttonStyle(.plain)
            .accessibilityIdentifier("today.hero")
    }

    private var card: some View {
        ItmoCard(tone: .primary, hero: true) {
            HStack(alignment: .firstTextBaseline) {
                Text(verbatim: hero.label.resolved)
                    .font(.itmo(.labelLarge, emphasized: true))
                    .foregroundStyle(ItmoColor.primary)
                Spacer(minLength: ItmoSpacing.compact)
                Text(verbatim: hero.countdown.resolved)
                    .font(.itmo(.labelLarge))
                    .foregroundStyle(.secondary)
            }
            Text(verbatim: hero.subject)
                .font(.itmo(.headlineSmall, emphasized: true))
                .lineLimit(3)
                .padding(.top, 2)
            Text(verbatim: hero.byline.resolved)
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
            if hero.online {
                online
            } else {
                place
            }
            if let note = hero.note {
                Text(verbatim: note).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
        }
    }

    private var online: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            // The small "join" sits next to the big word, at least a content gap from it (owner, 2026-10-13, hero lab
            // variant 2); under it when the row is too narrow for both.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 0) {
                    onlineTitle
                    Spacer(minLength: ItmoSpacing.content)
                    join
                }
                VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
                    onlineTitle
                    join
                }
            }
            Text(verbatim: hero.time).font(.itmo(.titleSmall)).foregroundStyle(.secondary).monospacedDigit()
        }
        .padding(.top, ItmoSpacing.related)
    }

    private var onlineTitle: some View {
        Text(verbatim: AppStrings.string("today_online")).font(.itmo(.displaySmall, emphasized: true))
    }

    @ViewBuilder private var join: some View {
        if let url = hero.videoUrl {
            ItmoProminentButton(title: AppStrings.string("today_join"), symbol: .videocam) { actions.join(url) }
                .controlSize(.small)
                .fixedSize()
                .accessibilityIdentifier("today.join")
        }
    }

    private var place: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            if let route = hero.route {
                // The small route button next to the big room, as "join" next to "online"; the address goes under them.
                HStack(spacing: 0) {
                    if let room = hero.room {
                        Text(verbatim: room.resolved).font(.itmo(.displayMedium, emphasized: true)).monospacedDigit()
                    }
                    Spacer(minLength: ItmoSpacing.content)
                    ItmoProminentButton(title: AppStrings.string("today_route"), symbol: .directions) { actions.route(route) }
                        .controlSize(.small)
                        .fixedSize()
                        .accessibilityIdentifier("today.route")
                }
                if let address = hero.address {
                    Text(verbatim: address).font(.itmo(.titleMedium))
                }
            } else if hero.room != nil || hero.address != nil {
                // The big room never clips: the address goes under it when the row is too narrow for both.
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) { roomAndAddress }
                    VStack(alignment: .leading, spacing: ItmoSpacing.related) { roomAndAddress }
                }
            }
            if let progress = hero.progress {
                ItmoProgressBar(value: Double(truncating: progress))
                HStack {
                    Text(verbatim: hero.start)
                    Spacer()
                    Text(verbatim: hero.end)
                }
                .font(.itmo(.labelMedium))
                .foregroundStyle(.secondary)
                .monospacedDigit()
            } else {
                Text(verbatim: hero.time).font(.itmo(.titleSmall)).foregroundStyle(.secondary).monospacedDigit()
            }
        }
        .padding(.top, ItmoSpacing.related)
    }

    @ViewBuilder private var roomAndAddress: some View {
        if let room = hero.room {
            Text(verbatim: room.resolved).font(.itmo(.displayMedium, emphasized: true)).monospacedDigit()
        }
        if let address = hero.address {
            Text(verbatim: address).font(.itmo(.titleMedium))
        }
    }
}
