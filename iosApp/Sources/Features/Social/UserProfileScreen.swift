import Shared
import SwiftUI

/// The person card (`AppRoutes.UserProfile`, also a `/u/<isu>` link and a friendship notification; DECISIONS.md,
/// person card) over the shared `UserProfileViewModel`, one card for any ISU. The header carries the name, so
/// the bar shows only back and share, as Contacts does. A part that failed says so with retry; a failed action
/// says why. Removing a friend and deleting the own review ask first. A user's schedule, sport and friends open with
/// the name the card shows; writing a review opens the editor sheet, a report the report sheet.
struct UserProfileScreen: View {
    let isu: Int32
    @Environment(AppRouter.self) private var router
    @State private var model: ObservableViewModel<UserProfileViewModel, any UserProfileUiState>
    @State private var messages = SocialMessages()
    @State private var removing: String?
    @State private var deletingReview = false
    @State private var partialFailure = false

    init(isu: Int32) {
        self.isu = isu
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            guard let model = store.resolve(type: UserProfileViewModel.self, parameters: userProfileParameters(isu: isu))
                as? UserProfileViewModel else {
                preconditionFailure("Koin resolved no UserProfileViewModel")
            }
            return model
        })
    }

    var body: some View {
        let page = model.state as? UserProfileUiStateContent
        UserProfileContent(state: model.state, actions: actions)
            .refreshable {
                await model.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                    $0.isRefreshing
                })
            }
            .userProfileBar(page) { SocialSharing.profile(name: $0.displayName.resolved, isu: $0.isu) }
            .alert(
                Text(verbatim: AppStrings.string("friends_remove_confirm_title")),
                isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
                presenting: removing
            ) { _ in
                Button(role: .destructive) { model.viewModel.removeFriend() } label: {
                    Text(verbatim: AppStrings.string("user_action_remove"))
                }
                Button(role: .cancel) {} label: { Text(verbatim: AppStrings.string("common_cancel")) }
            } message: { name in
                Text(verbatim: AppStrings.string("friends_remove_confirm_message", [name]))
            }
            .alert(Text(verbatim: AppStrings.string("teacher_review_delete_confirm")), isPresented: $deletingReview) {
                Button(role: .destructive) { model.viewModel.deleteOwnReview() } label: {
                    Text(verbatim: AppStrings.string("teacher_review_delete"))
                }
                Button(role: .cancel) {} label: { Text(verbatim: AppStrings.string("common_cancel")) }
            }
            .alert(Text(verbatim: AppStrings.string("common_partial_load_error")), isPresented: $partialFailure) {
                Button { model.viewModel.refresh(mode: .force) } label: { Text(verbatim: AppStrings.string("common_retry")) }
                Button(role: .cancel) {} label: { Text(verbatim: AppStrings.string("common_close")) }
            }
            .socialMessages(messages)
            .observing(model)
            .onEvents(of: model, \.events) { event in
                switch onEnum(of: event) {
                case let .actionFailed(failed): messages.failed(failed.error)
                case let .confirmRemove(confirm): removing = confirm.name.resolved
                case .loadFailed: partialFailure = true
                case .confirmDeleteReview: deletingReview = true
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("social.profile")
    }

    private var actions: UserProfileContentActions {
        let viewModel = model.viewModel
        return UserProfileContentActions(
            retry: { viewModel.refresh(mode: .force) },
            copyIsu: { messages.copy(isu: $0) },
            act: { page, action in
                switch action {
                case .writeReview: open(AppRoutes.ReviewEditor(args: page.reviewArgs))
                case .invite: SocialSharing.invitation()
                default: viewModel.onAction(action: action)
                }
            },
            removeFriend: { viewModel.requestRemoveFriend() },
            schedule: { open(AppRoutes.UserSchedule(isu: $0.isu, name: $0.displayName.resolved)) },
            sport: { open(AppRoutes.UserSport(isu: $0.isu, name: $0.displayName.resolved, givenName: $0.givenName)) },
            friends: { open(AppRoutes.UserFriends(isu: $0.isu, name: $0.displayName.resolved)) },
            toggleSummary: { viewModel.toggleSummaryScales() },
            review: TeacherReviewActions(
                vote: { viewModel.vote(reviewId: $0, up: $1) },
                report: { page, id in open(AppRoutes.ReportReview(args: page.reviewArgs, reviewId: id)) },
                author: { open(AppRoutes.UserProfile(isu: $0)) },
                source: { _ = SocialSharing.openLink($0) },
                edit: { open(AppRoutes.ReviewEditor(args: $0.reviewArgs)) },
                delete: { viewModel.requestDeleteOwnReview() }
            )
        )
    }

    private func open(_ route: any AppRoute) {
        messages.opened(router.open(route))
    }
}

extension View {
    /// The card's bar: back and share only, as Contacts; the header below carries the name. No title, as Android's
    /// card: a screen pushed from the card gets the system back label. The empty detail header also keeps
    /// `social.profile` off the list's `user_profile_list`: its bar is a container of its own.
    func userProfileBar(_ page: UserProfileUiStateContent?, share: @escaping (UserProfileUiStateContent) -> Void) -> some View {
        itmoDetailHeader(title: "", subtitle: nil)
            .toolbar {
                if let page {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { share(page) } label: { AppSymbol.share.image }
                            .accessibilityLabel(Text(verbatim: AppStrings.string("share_action")))
                            .accessibilityIdentifier("user_profile_share")
                    }
                }
            }
    }
}

extension UserProfileUiStateContent {
    /// The teacher of the page, as Android's `reviewArgs()`.
    var reviewArgs: TeacherReviewArgs { TeacherReviewArgs(teacherIsu: isu, teacherName: name) }
}

/// What the person card asks its host to do; the page is the one on screen at the tap.
struct UserProfileContentActions {
    var retry: () -> Void = {}
    var copyIsu: (Int32) -> Void = { _ in }
    var act: (UserProfileUiStateContent, CardAction) -> Void = { _, _ in }
    var removeFriend: () -> Void = {}
    var schedule: (UserProfileUiStateContent) -> Void = { _ in }
    var sport: (UserProfileUiStateContent) -> Void = { _ in }
    var friends: (UserProfileUiStateContent) -> Void = { _ in }
    var toggleSummary: () -> Void = {}
    var review = TeacherReviewActions()
}

/// The card without the ViewModel: a spinner until the parts settle, not found without an action, the load error with
/// the reason and retry, or the card: the centred header, the teaching and study blocks in the order the state
/// gives, and the remove-friend row last. Someone else's hidden data is simply absent.
struct UserProfileContent: View {
    let state: any UserProfileUiState
    var actions = UserProfileContentActions()

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state) {
            case .loading:
                ItmoLoadingView().accessibilityIdentifier("user_profile_loading")
            case let .error(error):
                if error.error is AppErrorNotFound {
                    ItmoEmptyView(symbol: .person, title: AppStrings.string("user_profile_not_found_title"))
                        .accessibilityIdentifier("user_profile_state")
                } else {
                    ItmoErrorView(
                        title: AppStrings.string("common_load_error_title"),
                        description: AppErrorTextsKt.toUiText(error.error).resolved,
                        retry: actions.retry
                    )
                    .accessibilityIdentifier("user_profile_state")
                }
            case let .content(page):
                card(page)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("user_profile_list")
    }

    @ViewBuilder
    private func card(_ page: UserProfileUiStateContent) -> some View {
        ProfileHeader(page: page, actions: actions)
        if page.teachingFirst {
            teaching(page)
            study(page)
        } else {
            study(page)
            teaching(page)
        }
        if page.canRemoveFriend {
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("user_profile_remove_friend"),
                    symbol: .remove,
                    tone: .bad,
                    danger: true,
                    action: actions.removeFriend
                )
                .accessibilityIdentifier("user_profile_remove")
            }
            .padding(.top, ItmoSpacing.content)
        }
    }

    /// Teaching: the lesson they give now, the subjects they teach the viewer and where to find them, then the reviews with their summary.
    @ViewBuilder
    private func teaching(_ page: UserProfileUiStateContent) -> some View {
        if let block = page.teaching {
            ItmoSectionHeader(title: AppStrings.string("person_section_teaching"))
                .accessibilityIdentifier("user_profile_teaching")
            if let now = block.now {
                // The card's one hero: where to find the teacher right now.
                ItmoCard(tone: .primary, hero: true) {
                    Text(verbatim: now.heading.resolved)
                        .font(.itmo(.labelLarge, emphasized: true))
                        .foregroundStyle(ItmoColor.primary)
                    Text(verbatim: now.place.resolved).font(.itmo(.headlineSmall, emphasized: true))
                    Text(verbatim: now.lesson.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("user_profile_now")
            }
            if !block.subjects.isEmpty || !block.rooms.isEmpty {
                ItmoListGroup {
                    ForEach(block.subjects, id: \.name) { subject in
                        ItmoRow(title: subject.name, subtitle: subject.note.resolved, symbol: .school, tone: .primary)
                    }
                    ForEach(Array(block.rooms.enumerated()), id: \.offset) { _, room in
                        ItmoRow(
                            title: AppStrings.string("person_section_rooms"),
                            subtitle: [room.title.resolved, room.detail].compactMap { $0 }.joined(separator: ", "),
                            symbol: .locationOn,
                            tone: .neutral
                        )
                    }
                }
            }
            if let reviews = page.reviews {
                ProfileReviewsSection(page: page, reviews: reviews, actions: actions)
            }
        }
    }

    /// Study: the groups, then the schedule, sport and friends the viewer may open.
    @ViewBuilder
    private func study(_ page: UserProfileUiStateContent) -> some View {
        if let block = page.study {
            ItmoSectionHeader(title: AppStrings.string("person_section_education"))
                .accessibilityIdentifier("user_profile_study")
            ItmoListGroup {
                ForEach(Array(block.groups.enumerated()), id: \.offset) { _, group in
                    ItmoRow(title: group.title.resolved, subtitle: group.detail, symbol: .group, tone: .tertiary)
                }
                if block.schedule {
                    opening("user_profile_schedule_title", .calendarMonth, id: "user_profile_schedule") { actions.schedule(page) }
                }
                if block.sport {
                    opening("user_profile_sport_title", .sprint, id: "user_profile_sport") { actions.sport(page) }
                }
                if block.friends {
                    opening("friends_title", .group, id: "user_profile_friends") { actions.friends(page) }
                }
            }
        }
    }

    private func opening(_ title: String, _ symbol: AppSymbol, id: String, action: @escaping () -> Void) -> some View {
        ItmoRow(title: AppStrings.string(title), symbol: symbol, tone: .primary, chevron: true, action: action)
            .accessibilityIdentifier(id)
    }
}

/// One centred block, as a contact card: avatar, full name, roles, the ISU with copy, then up to two full-width
/// buttons, the prominent one first; a single one carries its icon. At the accessibility sizes the buttons stack.
struct ProfileHeader: View {
    let page: UserProfileUiStateContent
    let actions: UserProfileContentActions

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(spacing: 0) {
            ItmoAvatar(initials: page.initials, pictureUrl: page.pictureUrl, size: 96)
            Text(verbatim: page.displayName.resolved)
                .font(.itmo(.headlineSmall, emphasized: true))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 14)
                .accessibilityAddTraits(.isHeader)
            if !page.roles.isEmpty {
                ItmoFlow(spacing: 6, centered: true) {
                    ForEach(Array(page.roles.enumerated()), id: \.offset) { _, role in
                        ItmoStatusPill(pill: role.pill)
                    }
                }
                .padding(.top, 10)
            }
            isu
            if !page.actions.isEmpty {
                let layout = typeSize.isAccessibilitySize
                    ? AnyLayout(VStackLayout(spacing: 10)) : AnyLayout(HStackLayout(spacing: 10))
                layout {
                    ForEach(Array(page.actions.enumerated()), id: \.offset) { index, action in
                        button(action, index: index)
                    }
                }
                .itmoTint()
                .disabled(page.busy)
                .padding(.top, 18)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, ItmoSpacing.compact)
        .padding(.bottom, 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("user_profile_hero")
    }

    /// The ISU with a copy symbol: a tap copies the number, which the host confirms.
    private var isu: some View {
        Button { actions.copyIsu(page.isu) } label: {
            HStack(spacing: 6) {
                Text(verbatim: isuText("user_subtitle_isu", page.isu))
                AppSymbol.contentCopy.image.font(.itmo(.bodySmall))
            }
            .font(.itmo(.bodyMedium))
            .foregroundStyle(.secondary)
            .frame(minHeight: ItmoMetrics.touchTarget)
            .padding(.horizontal, ItmoSpacing.compact)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: isuText("person_isu_copy", page.isu)))
        .accessibilityIdentifier("user_profile_isu")
    }

    @ViewBuilder
    private func button(_ action: CardAction, index: Int) -> some View {
        let single = page.actions.count == 1
        let label = Group {
            if single {
                Label { Text(verbatim: action.label.resolved) } icon: { action.symbol.image }
            } else {
                Text(verbatim: action.label.resolved)
            }
        }
        .font(.itmo(.labelLarge, emphasized: true))
        .lineLimit(typeSize.isAccessibilitySize ? nil : 1)
        .frame(maxWidth: .infinity)
        let id = action == .writeReview ? "user_profile_write_review"
            : index == 0 ? "user_profile_primary" : "user_profile_secondary"
        if index == 0 {
            Button { actions.act(page, action) } label: { label.foregroundStyle(ItmoColor.onPrimary) }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .accessibilityIdentifier(id)
        } else {
            Button { actions.act(page, action) } label: { label }
                .buttonStyle(.glass)
                .controlSize(.large)
                .accessibilityIdentifier(id)
        }
    }
}

extension CardAction {
    var symbol: AppSymbol {
        switch self {
        case .writeReview: .editNote
        case .addFriend: .add
        case .acceptRequest: .check
        case .rejectRequest: .close
        case .cancelRequest: .undo
        case .invite: .share
        }
    }
}

extension ProfileRole {
    /// A position in the primary tone, a study group in the tertiary, the friendship with a check.
    var pill: ItmoPill {
        switch kind {
        case .position: ItmoPill(text: text.resolved, tone: .primary)
        case .study: ItmoPill(text: text.resolved, tone: .tertiary)
        case .friend: ItmoPill(text: text.resolved, tone: .ok, symbol: .check)
        case .own: ItmoPill(text: text.resolved, tone: .neutral)
        }
    }
}
