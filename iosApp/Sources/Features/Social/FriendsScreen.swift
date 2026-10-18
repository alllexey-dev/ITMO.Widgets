import Shared
import SwiftUI

/// Friends (`AppRoutes.Friends`, DECISIONS.md) over the shared `FriendsViewModel`, pushed on the profile stack: the
/// search field on top (in the content, under the static title, as the sport sign-up's), under it the incoming
/// requests with accept and reject, the sent ones with cancel, then the friends, who open their card (removing a friend lives there), and the profile share row. A typed
/// query shows the people found in place of the lists. Without the connection the gate says why.
struct FriendsScreen: View {
    @Environment(AppRouter.self) private var router
    @State private var model = ObservableViewModel(FriendsViewModel.self, state: \.uiState)
    @State private var messages = SocialMessages()
    /// The field's own text: bound to the ViewModel's `query`, which arrives a frame late, the field would lose the
    /// keystrokes typed meanwhile.
    @State private var query = ""

    var body: some View {
        let state = model.state
        FriendsContent(state: state, query: $query, actions: actions)
            .onChange(of: query) { _, text in model.viewModel.onQueryChanged(text: text) }
            .refreshable { model.viewModel.refresh(mode: .pull) }
            .itmoDetailHeader(
                title: AppStrings.string("friends_title"),
                subtitle: (state.lists as? FriendListsContent)?.subtitle?.resolved
            )
            .socialMessages(messages)
            .observing(model)
            .onEvents(of: model, \.events) { event in
                switch onEnum(of: event) {
                case let .actionFailed(failed): messages.failed(failed.error)
                case .invite: SocialSharing.invitation()
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("social.friends")
    }

    private var actions: FriendsContentActions {
        let viewModel = model.viewModel
        return FriendsContentActions(
            open: { messages.opened(router.open(AppRoutes.UserProfile(isu: $0.isu))) },
            act: { viewModel.onAction(row: $0, action: $1) },
            retry: { viewModel.refresh(mode: .force) },
            retrySearch: { viewModel.retrySearch() },
            loadMore: { viewModel.loadMore() },
            connect: { messages.opened(router.open(AppRoutes.Settings(page: "SERVICES"))) },
            share: { SocialSharing.profile(name: $0.name, isu: $0.isu) }
        )
    }
}

/// What Friends asks its host to do.
struct FriendsContentActions {
    var open: (UserRowUi) -> Void = { _ in }
    var act: (UserRowUi, UserAction) -> Void = { _, _ in }
    var retry: () -> Void = {}
    var retrySearch: () -> Void = {}
    var loadMore: () -> Void = {}
    var connect: () -> Void = {}
    var share: (UserSummary) -> Void = { _ in }
}

/// Friends without the ViewModel: the gate, the search results or the lists.
struct FriendsContent: View {
    let state: FriendsUiState
    @Binding var query: String
    var actions = FriendsContentActions()

    var body: some View {
        ItmoScreenStack {
            if !(state.lists is FriendListsDisabled) {
                ItmoSearchField(
                    placeholder: AppStrings.string("user_search_hint"),
                    query: $query,
                    clearLabel: AppStrings.string("user_search_clear")
                )
                .accessibilityIdentifier("friends_search")
            }
            if state.lists is FriendListsDisabled {
                ItmoGateCard(
                    title: AppStrings.string("friends_disabled_title"),
                    reason: AppStrings.string("friends_disabled_description"),
                    fix: (AppStrings.string("friends_connect"), actions.connect),
                    symbol: .group
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("friends_gate")
            } else if let search = state.search {
                results(search)
            } else {
                lists
            }
        }
        .accessibilityIdentifier("friends_list")
    }

    @ViewBuilder
    private func results(_ search: any PeopleSearchResults) -> some View {
        switch onEnum(of: search) {
        case .loading:
            ItmoLoadingView()
        case .empty:
            ItmoEmptyView(symbol: .search, title: AppStrings.string("user_search_empty_title"))
                .accessibilityIdentifier("friends_state")
        case let .error(error):
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(error.error).resolved,
                retry: actions.retrySearch
            )
            .accessibilityIdentifier("friends_state")
        case let .content(found):
            PersonGroup(
                rows: found.registered,
                title: AppStrings.string("user_search_section_registered"),
                open: actions.open,
                act: actions.act
            )
            PersonGroup(
                rows: found.others,
                title: AppStrings.string("user_search_section_others"),
                open: actions.open,
                act: actions.act
            )
            if found.offersMore || found.loadingMore {
                Button(action: actions.loadMore) {
                    ZStack {
                        Text(verbatim: AppStrings.string("user_search_load_more")).opacity(found.loadingMore ? 0 : 1)
                        if found.loadingMore { ProgressView() }
                    }
                    .font(.itmo(.labelLarge, emphasized: true))
                    .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
                }
                .buttonStyle(.borderless)
                .itmoTint()
                .disabled(found.loadingMore)
                .accessibilityIdentifier("user_list_load_more")
            }
        }
    }

    @ViewBuilder private var lists: some View {
        switch onEnum(of: state.lists) {
        case .loading, .disabled:
            ItmoLoadingView()
        case let .error(error):
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(error.error).resolved,
                retry: actions.retry
            )
            .accessibilityIdentifier("friends_state")
        case let .content(lists):
            let me = state.viewer
            if lists.isEmpty {
                ItmoEmptyView(
                    symbol: .group,
                    title: AppStrings.string("friends_empty_title"),
                    description: AppStrings.string("friends_empty_description"),
                    action: me.map { me in (AppStrings.string("friends_share_title"), { actions.share(me) }) }
                )
                .accessibilityIdentifier("friends_state")
            } else {
                PersonGroup(
                    rows: lists.incoming + lists.outgoing,
                    title: AppStrings.string("friends_tab_requests"),
                    open: actions.open,
                    act: actions.act
                )
                PersonGroup(
                    rows: lists.friends,
                    title: AppStrings.string("friends_tab_friends"),
                    count: "\(lists.friends.count)",
                    open: actions.open,
                    act: actions.act
                )
                if let me {
                    ItmoListGroup {
                        ItmoRow(
                            title: AppStrings.string("friends_share_title"),
                            subtitle: AppStrings.string("friends_share_subtitle"),
                            symbol: .share,
                            tone: .primary,
                            chevron: true,
                            action: { actions.share(me) }
                        )
                        .accessibilityIdentifier("friends_share")
                    }
                    .padding(.top, ItmoSpacing.content)
                }
            }
        }
    }
}
