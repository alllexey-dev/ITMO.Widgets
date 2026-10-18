import Shared
import SwiftUI

/// Another user's friends (`AppRoutes.UserFriends`, from a card) over the shared `UserFriendsViewModel`: the list in
/// groups that open each card, an empty list with refresh, nothing for an owner who hides it, the gate without the
/// connection, the error with retry. A refresh that failed over a shown list says why.
struct UserFriendsScreen: View {
    let isu: Int32
    let name: String
    @Environment(AppRouter.self) private var router
    @State private var model: ObservableViewModel<UserFriendsViewModel, any UserFriendsUiState>
    @State private var messages = SocialMessages()

    init(isu: Int32, name: String) {
        self.isu = isu
        self.name = name
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = userFriendsParameters(isu: isu, name: name)
            guard let model = store.resolve(type: UserFriendsViewModel.self, parameters: parameters)
                as? UserFriendsViewModel else {
                preconditionFailure("Koin resolved no UserFriendsViewModel")
            }
            return model
        })
    }

    var body: some View {
        UserFriendsContent(
            state: model.state,
            open: { messages.opened(router.open(AppRoutes.UserProfile(isu: $0.isu))) },
            retry: { model.viewModel.refresh(mode: .force) },
            connect: { messages.opened(router.open(AppRoutes.Settings(page: "SERVICES"))) }
        )
        .refreshable { model.viewModel.refresh(mode: .pull) }
        .itmoDetailHeader(title: AppStrings.string("friends_title"), subtitle: name.isEmpty ? nil : name)
        .socialMessages(messages)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            if let failed = event as? UserFriendsEventRefreshFailed {
                messages.show(AppErrorTextsKt.toUiText(failed.error).resolved)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("social.userFriends")
    }
}

/// Another user's friends without the ViewModel.
struct UserFriendsContent: View {
    let state: any UserFriendsUiState
    var open: (UserRowUi) -> Void = { _ in }
    var retry: () -> Void = {}
    var connect: () -> Void = {}

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state) {
            case .loading:
                ItmoLoadingView().accessibilityIdentifier("user_friends_loading")
            case .hidden:
                ItmoEmptyView(symbol: .group, title: AppStrings.string("user_friends_empty_title"))
            case .disabled:
                ItmoEmptyView(
                    symbol: .group,
                    title: AppStrings.string("friends_disabled_title"),
                    description: AppStrings.string("friends_disabled_description"),
                    action: (AppStrings.string("friends_connect"), connect)
                )
            case let .error(error):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(error.error).resolved,
                    retry: retry
                )
            case let .content(content):
                if content.items.isEmpty {
                    ItmoEmptyView(
                        symbol: .group,
                        title: AppStrings.string("user_friends_empty_title"),
                        action: (AppStrings.string("user_friends_refresh"), retry)
                    )
                } else {
                    PersonList(items: content.items, open: open)
                }
            }
        }
        .accessibilityIdentifier("user_friends_list")
    }
}
