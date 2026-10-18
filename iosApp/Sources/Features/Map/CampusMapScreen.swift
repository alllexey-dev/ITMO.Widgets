import Shared
import SwiftUI

/// The campus map (`AppRoutes.CampusMap`, docs/features/map.md) over the shared `CampusMapViewModel`: the native plan
/// under everything, the search and the building chips on top, the floors on the edge, the room card, the route step
/// or the results at the bottom. "Open in Maps" opens Apple Maps through `PlatformActions`.
struct CampusMapScreen: View {
    @State private var model: ObservableViewModel<CampusMapViewModel, CampusMapUiState>
    @Environment(\.colorScheme) private var colorScheme

    init(route: AppRoutes.CampusMap) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = CampusMapIosModuleKt.campusMapParameters(route: route)
            guard let model = store.resolve(type: CampusMapViewModel.self, parameters: parameters) as? CampusMapViewModel else {
                preconditionFailure("Koin resolved no CampusMapViewModel")
            }
            return model
        })
    }

    var body: some View {
        let viewModel = model.viewModel
        CampusMapContent(
            state: model.state,
            actions: CampusMapContentActions(
                retry: { viewModel.retry() },
                building: { viewModel.selectBuilding(buildingId: $0) },
                level: { viewModel.selectLevel(level: $0) },
                search: { viewModel.search(query: $0) },
                place: { viewModel.selectPlace(place: $0) },
                result: { viewModel.openResult(result: $0) },
                route: { viewModel.route() },
                step: { viewModel.step(index: $0) },
                closeTrip: { viewModel.closeTrip() },
                closeRoom: { viewModel.closeRoom() },
                openMaps: { destination in
                    guard let actions = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions else { return }
                    _ = actions.openMap(destination: destination)
                }
            )
        ) { scene in
            IndoorMapView(scene: scene, dark: colorScheme == .dark) { viewModel.selectRoom(roomId: $0) }
        }
        .itmoDetailHeader(title: AppStrings.string("map_title"), subtitle: "")
        .observing(model)
        .accessibilityIdentifier("map")
    }
}

/// What the map asks its host to do.
struct CampusMapContentActions {
    var retry: () -> Void = {}
    var building: (Int32) -> Void = { _ in }
    var level: (Int32) -> Void = { _ in }
    var search: (String) -> Void = { _ in }
    var place: (Place) -> Void = { _ in }
    var result: (SearchResult) -> Void = { _ in }
    var route: () -> Void = {}
    var step: (Int32) -> Void = { _ in }
    var closeTrip: () -> Void = {}
    var closeRoom: () -> Void = {}
    var openMaps: (MapDestination) -> Void = { _ in }
}

/// The map without the ViewModel; [map] draws the native plan, snapshot tests pass `MapPlaceholder`.
struct CampusMapContent<Map: View>: View {
    let state: CampusMapUiState
    var actions = CampusMapContentActions()
    @ViewBuilder let map: (MapScene) -> Map

    var body: some View {
        switch onEnum(of: state) {
        case .loading:
            ItmoLoadingView()
        case let .failed(failed):
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(failed.error).resolved,
                retry: actions.retry
            )
        case let .noPlan(noPlan):
            ItmoEmptyView(
                symbol: .map,
                title: AppStrings.string("map_no_plan_title"),
                description: noPlan.building,
                action: noPlan.destination.map { destination in
                    (title: AppStrings.string("map_open_in_maps"), run: { actions.openMaps(destination) })
                }
            )
        case let .plan(plan):
            PlanView(plan: plan, actions: actions, map: map)
        }
    }
}

private struct PlanView<Map: View>: View {
    let plan: CampusMapUiStatePlan
    let actions: CampusMapContentActions
    let map: (MapScene) -> Map

    var body: some View {
        map(plan.map)
            .ignoresSafeArea(edges: .bottom)
            .overlay(alignment: .top) { top }
            .safeAreaInset(edge: .bottom) { bottom }
    }

    private var top: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            ItmoSearchField(
                placeholder: AppStrings.string("map_search_hint"),
                query: Binding(get: { plan.search.query }, set: { actions.search($0) }),
                clearLabel: AppStrings.string("map_close")
            )
            .padding(.horizontal, ItmoSpacing.screenMargin)
            if plan.buildings.count > 1, !plan.search.active {
                ItmoChips(chips: plan.buildings.map { ItmoChip(label: $0.label, selected: $0.selected) }) { index in
                    actions.building(plan.buildings[index].id)
                }
            }
            if plan.trip == nil, plan.search.query.isEmpty {
                ItmoChips(chips: plan.search.places.map { ItmoChip(label: $0.label.resolved, selected: $0.selected) }) { index in
                    actions.place(plan.search.places[index].place)
                }
            }
            // Under the chips, never over them; the results cover the edge, so the floors return with the plan.
            if !plan.search.active {
                HStack {
                    Spacer(minLength: 0)
                    floors
                }
                .padding(.trailing, ItmoSpacing.related)
            }
        }
        .padding(.top, ItmoSpacing.related)
    }

    private var floors: some View {
        VStack(spacing: 2) {
            ForEach(plan.levels.reversed(), id: \.level) { level in
                Button { actions.level(level.level) } label: {
                    Text(verbatim: level.label.resolved)
                        .font(.itmo(.labelLarge, emphasized: true))
                        .frame(width: 44, height: 44)
                        .foregroundStyle(level.selected ? ItmoColor.onPrimary : ItmoColor.onSurface)
                        .background(level.selected ? ItmoColor.primary : Color.clear, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(level.selected ? .isSelected : [])
            }
        }
        .padding(4)
        .glassEffect(.regular, in: Capsule())
        .accessibilityElement(children: .contain)
        .accessibilityLabel(AppStrings.string("map_floors"))
    }

    @ViewBuilder private var bottom: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            if plan.search.active {
                results
            } else if let trip = plan.trip {
                tripCard(trip)
            } else if let room = plan.room {
                roomCard(room)
            } else if let notice = plan.notice {
                ItmoCard { Text(verbatim: notice.resolved).font(.itmo(.bodyLarge)) }
            }
            Text(verbatim: plan.map.attribution)
                .font(.itmo(.labelSmall))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, ItmoSpacing.screenMargin)
        .padding(.bottom, ItmoSpacing.compact)
    }

    private func roomCard(_ room: RoomCard) -> some View {
        ItmoCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: room.title.resolved).font(.itmo(.headlineSmall, emphasized: true))
                    Text(verbatim: room.subtitle.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                    if let note = room.note {
                        Text(verbatim: note.resolved).font(.itmo(.bodyMedium)).foregroundStyle(ItmoColor.tertiary)
                    }
                }
                Spacer(minLength: ItmoSpacing.content)
                closeButton(actions.closeRoom)
            }
            HStack(spacing: ItmoSpacing.content) {
                ItmoProminentButton(title: AppStrings.string("map_route"), symbol: .directions, action: actions.route)
                    .accessibilityIdentifier("map.route")
                if let destination = room.destination {
                    Button(AppStrings.string("map_open_in_maps")) { actions.openMaps(destination) }
                        .buttonStyle(.glass)
                }
            }
            .padding(.top, ItmoSpacing.compact)
        }
        .accessibilityIdentifier("map.room")
    }

    private func tripCard(_ trip: TripView) -> some View {
        let step = trip.step
        return ItmoCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: ItmoSpacing.content) {
                        Text(verbatim: trip.from.resolved)
                        Text(verbatim: step.counter.resolved)
                    }
                    .font(.itmo(.labelLarge))
                    .foregroundStyle(.secondary)
                    Text(verbatim: step.title.resolved).font(.itmo(.titleLarge, emphasized: true))
                }
                Spacer(minLength: ItmoSpacing.content)
                closeButton(actions.closeTrip)
            }
            Text(verbatim: step.text.resolved).font(.itmo(.bodyLarge))
            HStack(spacing: ItmoSpacing.content) {
                Button { actions.step(trip.current - 1) } label: { AppSymbol.arrowBack.image }
                    .buttonStyle(.glass)
                    .disabled(trip.current == 0)
                    .accessibilityLabel(AppStrings.string("map_route_previous"))
                Button { actions.step(trip.current + 1) } label: { AppSymbol.arrowForward.image }
                    .buttonStyle(.glass)
                    .disabled(Int(trip.current) >= trip.steps.count - 1)
                    .accessibilityLabel(AppStrings.string("map_route_next"))
                Spacer(minLength: 0)
                if let destination = step.destination {
                    ItmoProminentButton(title: AppStrings.string("map_open_in_maps"), symbol: .openInNew) { actions.openMaps(destination) }
                }
            }
            .padding(.top, ItmoSpacing.compact)
        }
        .accessibilityIdentifier("map.trip")
    }

    private var results: some View {
        ItmoCard {
            if plan.search.empty {
                Text(verbatim: AppStrings.string("map_search_empty")).font(.itmo(.bodyLarge))
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(plan.search.results, id: \.roomId) { resultRow($0) }
                        if !plan.search.elsewhere.isEmpty {
                            Text(verbatim: AppStrings.string("map_search_other_buildings"))
                                .font(.itmo(.labelLarge, emphasized: true))
                                .foregroundStyle(ItmoColor.primary)
                                .padding(.vertical, ItmoSpacing.compact)
                            ForEach(plan.search.elsewhere, id: \.self) { resultRow($0) }
                        }
                    }
                }
                .frame(maxHeight: 320)
            }
        }
    }

    private func resultRow(_ result: SearchResult) -> some View {
        Button { actions.result(result) } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: result.title.resolved).font(.itmo(.bodyLarge))
                Text(verbatim: result.subtitle.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func closeButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) { AppSymbol.close.image }
            .buttonStyle(.glass)
            .accessibilityLabel(AppStrings.string("map_close"))
    }
}

/// The map's place where no GL view draws (snapshot tests).
struct MapPlaceholder: View {
    var body: some View {
        ZStack {
            Color(.secondarySystemBackground)
            AppSymbol.map.image.font(.largeTitle).foregroundStyle(.tertiary)
        }
    }
}
