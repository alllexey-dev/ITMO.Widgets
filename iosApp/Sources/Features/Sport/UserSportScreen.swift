import Shared
import SwiftUI

/// Another user's sport (`AppRoutes.UserSport`, from a profile) over the shared `UserSportViewModel`, pushed on the
/// stack, read-only: the confirmed lessons and pending queues by day as lesson cards, a card opens its building in
/// Apple Maps. The title is `user_profile_sport_title` with the user's given name after it, as their friends' page.
/// Privacy shows the lock with nothing to retry, an error the retry, no bookings the empty state; a pull refreshes.
struct UserSportScreen: View {
    @State private var model: ObservableViewModel<UserSportViewModel, any UserSportUiState>

    init(isu: Int32, name: String, givenName: String?) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = userSportParameters(isu: isu, name: name, givenName: givenName)
            guard let model = store.resolve(type: UserSportViewModel.self, parameters: parameters)
                as? UserSportViewModel else {
                preconditionFailure("Koin resolved no UserSportViewModel")
            }
            return model
        })
    }

    var body: some View {
        UserSportContent(
            state: model.state,
            time: Self.time,
            openMap: Self.openBuilding,
            retry: { model.viewModel.refresh(mode: .force) }
        )
        .refreshable {
            await model.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                ($0.uiState.value as? UserSportUiStateContent)?.refreshing == true
            })
        }
        .itmoDetailHeader(
            title: AppStrings.string("user_profile_sport_title"),
            subtitle: model.viewModel.givenName.isEmpty ? nil : model.viewModel.givenName
        )
        .observing(model)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sport.user")
    }

    private static var time: AcademicTimeProvider {
        guard let time = IosKoin.shared.get(protocol: AcademicTimeProvider.self) as? AcademicTimeProvider else {
            preconditionFailure("Koin resolved no AcademicTimeProvider")
        }
        return time
    }

    /// The booking's building from its room, as Android's `geo:` query: nothing opens when the room names none.
    private static func openBuilding(_ booking: SportBooking) {
        guard let address = booking.extractBuildingAddress(),
              let actions = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions else { return }
        _ = actions.openMap(destination: MapDestination(label: address, address: address, latitude: nil, longitude: nil))
    }
}

/// Another user's sport without the ViewModel.
struct UserSportContent: View {
    let state: any UserSportUiState
    let time: AcademicTimeProvider
    var openMap: (SportBooking) -> Void = { _ in }
    var retry: () -> Void = {}

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state) {
            case .loading:
                ItmoLoadingView().accessibilityIdentifier("user_sport_loading")
            case let .error(error):
                if error.error is AppErrorForbidden {
                    ItmoEmptyView(
                        symbol: .lock,
                        title: AppStrings.string("user_sport_hidden_title"),
                        description: AppStrings.string("user_profile_hidden")
                    )
                    .accessibilityIdentifier("user_sport_state")
                } else {
                    ItmoErrorView(
                        title: AppStrings.string("common_load_error_title"),
                        description: AppErrorTextsKt.toUiText(error.error).resolved,
                        retry: retry
                    )
                    .accessibilityIdentifier("user_sport_state")
                }
            case let .content(content):
                if content.bookings.isEmpty {
                    ItmoEmptyView(
                        symbol: .exercise,
                        title: AppStrings.string("user_sport_empty_title"),
                        description: AppStrings.string("user_sport_empty_description")
                    )
                    .accessibilityIdentifier("user_sport_state")
                } else {
                    days(content.bookings)
                }
            }
        }
    }

    @ViewBuilder
    private func days(_ bookings: [SportBooking]) -> some View {
        let byId = Dictionary(bookings.map { ($0.lessonId, $0) }, uniquingKeysWith: { first, _ in first })
        VStack(alignment: .leading, spacing: ItmoKit.gap) {
            ForEach(userSportDays(bookings: bookings, time: time), id: \.self) { day in
                SportDayLabel(text: day.title.resolved)
                ForEach(day.bookings, id: \.lessonId) { item in
                    Button { byId[item.lessonId].map(openMap) } label: {
                        ItmoLessonCard(lesson: item.lesson.kitLesson(id: "\(item.lessonId)"))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("user_sport.booking.\(item.lessonId)")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("user_sport_list")
    }
}
