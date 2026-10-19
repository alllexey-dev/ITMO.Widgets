import Shared
import SwiftUI

/// The campus map (`AppRoutes.CampusMap`, docs/features/map.md) over the shared `CampusMapViewModel`: the native plan
/// under everything, the search, the building pill and the place chips on top, the floors on the edge, a compact room
/// card, route or results at the bottom. "Open in Maps" (no plan, the city step) opens Apple Maps through `PlatformActions`.
struct CampusMapScreen: View {
    @State private var model: ObservableViewModel<CampusMapViewModel, CampusMapUiState>

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
                routeFrom: { viewModel.routeFrom() },
                pick: { viewModel.pick(end: $0) },
                swap: { viewModel.swap() },
                fitBuilding: { viewModel.fitBuilding() },
                step: { viewModel.step(index: $0) },
                closeTrip: { viewModel.closeTrip() },
                closeRoom: { viewModel.closeRoom() },
                openMaps: { destination in
                    guard let actions = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions else { return }
                    _ = actions.openMap(destination: destination)
                }
            )
        ) { scene in
            IndoorMapView(scene: scene) { viewModel.selectRoom(roomId: $0) }
        }
        .campusMapChrome()
        .observing(model)
        .accessibilityIdentifier("map")
    }
}

extension View {
    /// The map's bar: the system bar with the back button and an inline title. The shell drops the tab bar, the pass
    /// accessory and the demo banner for it (`RouteTarget.fullScreen`).
    func campusMapChrome() -> some View {
        navigationTitle(Text(verbatim: AppStrings.string("map_title")))
            .toolbarTitleDisplayMode(.inline)
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
    var routeFrom: () -> Void = {}
    var pick: (RouteEnd?) -> Void = { _ in }
    var swap: () -> Void = {}
    var fitBuilding: () -> Void = {}
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
    @FocusState private var searchFocused: Bool
    /// The text the field showed when the map ended the search: UIKit writes it back when the field loses focus, and
    /// that write is not the user's.
    @State private var staleQuery: String?

    var body: some View {
        map(plan.map)
            .ignoresSafeArea(edges: .bottom)
            .overlay(alignment: .top) { top }
            .overlay {
                if plan.loading { loading }
            }
            .safeAreaInset(edge: .bottom) { bottom }
            // Picking a route end starts in the search, as itmo-map-web's from/to search.
            .onChange(of: plan.trip?.picking) { _, picking in
                if picking != nil { searchFocused = true } else { endSearch() }
            }
            .onChange(of: searchFocused) { _, focused in
                if focused { staleQuery = nil }
            }
    }

    private var picking: RouteEnd? { plan.trip?.picking }

    /// An opened result or a picked route end ends the search: the keyboard goes away.
    private func endSearch() {
        guard searchFocused else { return }
        staleQuery = plan.search.query
        searchFocused = false
    }

    private var top: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            ItmoSearchField(
                placeholder: AppStrings.string(searchPlaceholder),
                query: Binding(get: { plan.search.query }, set: { query in
                    if query == staleQuery {
                        staleQuery = nil
                    } else {
                        actions.search(query)
                    }
                }),
                clearLabel: AppStrings.string("map_close")
            )
            .focused($searchFocused)
            .accessibilityIdentifier("map.search")
            .background(ItmoColor.surface, in: .capsule)
            .mapChrome()
            .padding(.horizontal, ItmoSpacing.screenMargin)
            if plan.search.query.isEmpty {
                chips
            }
            // The results cover the edge, so the floors return with the plan; a route end can be tapped on any floor.
            if !plan.search.active || plan.trip != nil {
                HStack(alignment: .top) {
                    Spacer(minLength: 0)
                    VStack(spacing: ItmoSpacing.compact) {
                        floors
                        fitButton
                    }
                }
                .padding(.trailing, ItmoSpacing.compact)
            }
        }
        .padding(.top, ItmoSpacing.related)
    }

    private var searchPlaceholder: String {
        switch picking {
        case .from: "map_route_pick_from"
        case .to: "map_route_pick_to"
        default: "map_search_hint"
        }
    }

    /// The building pill and the places with their map icons, opaque like itmo-map-web's so they read over the plan.
    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ItmoSpacing.compact) {
                buildingPill
                if plan.trip == nil || picking != nil {
                    ForEach(plan.search.places, id: \.place) { chip in
                        Button { actions.place(chip.place) } label: {
                            HStack(spacing: 6) {
                                MapSprite(name: chip.icon, size: 24)
                                Text(verbatim: chip.label.resolved)
                            }
                            .mapChip(selected: chip.selected)
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(chip.selected ? .isSelected : [])
                        .accessibilityIdentifier("map.place")
                    }
                }
            }
            .padding(.horizontal, ItmoSpacing.screenMargin)
            .padding(.vertical, ItmoSpacing.related)
        }
    }

    @ViewBuilder private var buildingPill: some View {
        if let current = plan.buildings.first(where: { $0.selected }) {
            Menu {
                ForEach(plan.buildings, id: \.id) { building in
                    Button { actions.building(building.id) } label: {
                        if building.selected {
                            Label { Text(verbatim: building.label) } icon: { AppSymbol.check.image }
                        } else {
                            Text(verbatim: building.label)
                        }
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    AppSymbol.locationOn.image.foregroundStyle(ItmoColor.primary)
                    Text(verbatim: current.label)
                    AppSymbol.expandMore.image.font(.itmo(.labelSmall))
                }
                .mapChip(selected: false)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(verbatim: "\(AppStrings.string("map_buildings")): \(current.label)"))
            .accessibilityIdentifier("map.building")
        }
    }

    /// itmo-map-web's "show the whole building" under the floors: the camera back to the whole building.
    private var fitButton: some View {
        Button(action: actions.fitBuilding) {
            AppSymbol.fitScreen.image
                .frame(width: 44, height: 44)
                .background(ItmoColor.surface, in: Circle())
                .contentShape(Circle())
                .mapChrome()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(AppStrings.string("map_fit_building"))
        .accessibilityIdentifier("map.fit")
    }

    private var loading: some View {
        HStack(spacing: ItmoSpacing.content) {
            ProgressView()
            Text(verbatim: AppStrings.string("map_loading")).font(.itmo(.labelLarge))
        }
        .padding(.horizontal, ItmoSpacing.group)
        .padding(.vertical, ItmoSpacing.content)
        .background(ItmoColor.surface, in: .capsule)
        .mapChrome()
    }

    /// itmo-map-web's floor selector: a white column with a shadow, the floor in the accent, a dot on the route's floors.
    private var floors: some View {
        VStack(spacing: 2) {
            ForEach(plan.levels.reversed(), id: \.level) { level in
                Button { actions.level(level.level) } label: {
                    Text(verbatim: level.label.resolved)
                        .font(.itmo(.titleSmall, emphasized: level.selected))
                        .frame(width: 40, height: 40)
                        .foregroundStyle(level.selected ? ItmoColor.onPrimary : ItmoColor.onSurfaceVariant)
                        .background(level.selected ? ItmoColor.primary : Color.clear, in: Circle())
                        .overlay(alignment: .topTrailing) {
                            if level.onRoute {
                                Circle()
                                    .fill(level.selected ? ItmoColor.onPrimary : ItmoColor.primary)
                                    .frame(width: 7, height: 7)
                                    .overlay(Circle().stroke(level.selected ? ItmoColor.primary : ItmoColor.surface, lineWidth: 2))
                                    .padding(5)
                            }
                        }
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(level.selected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(ItmoColor.surface, in: RoundedRectangle(cornerRadius: 24))
        .mapChrome()
        .accessibilityElement(children: .contain)
        .accessibilityLabel(AppStrings.string("map_floors"))
    }

    @ViewBuilder private var bottom: some View {
        Group {
            if let trip = plan.trip {
                tripCard(trip)
            } else if plan.search.active {
                sheet { results(maxHeight: 280) }
            } else if let room = plan.room {
                roomCard(room)
            } else if let notice = plan.notice {
                ItmoCard { Text(verbatim: notice.resolved).font(.itmo(.bodyLarge)) }
            }
        }
        .padding(.horizontal, ItmoSpacing.compact)
        .padding(.bottom, ItmoSpacing.compact)
    }

    /// The bottom card of the map: opaque with a shadow, compact so the plan keeps the screen.
    private func sheet(@ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .padding(.vertical, ItmoSpacing.compact)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ItmoColor.surface, in: RoundedRectangle(cornerRadius: 28))
            .mapChrome()
            .accessibilityElement(children: .contain)
    }

    /// The room: its icon, number and floor, route here and route from here (itmo-map-web's unit card).
    private func roomCard(_ room: RoomCard) -> some View {
        sheet {
            HStack(alignment: .center, spacing: ItmoSpacing.content) {
                MapSprite(name: room.icon, size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: room.title.resolved).font(.itmo(.titleLarge, emphasized: true)).lineLimit(1)
                    Text(verbatim: room.subtitle.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary).lineLimit(1)
                    if let note = room.note {
                        Text(verbatim: note.resolved).font(.itmo(.bodyMedium)).foregroundStyle(ItmoColor.tertiary)
                    }
                }
                Spacer(minLength: 0)
                closeButton(actions.closeRoom)
            }
            .padding(.horizontal, ItmoSpacing.group)
            HStack(spacing: ItmoSpacing.compact) {
                // ItmoProminentButton's look (onPrimary on the glass prominent primary), stretched to half the card.
                Button(action: actions.route) {
                    Label { Text(verbatim: AppStrings.string("map_route_here")) } icon: { AppSymbol.directions.image }
                        .foregroundStyle(ItmoColor.onPrimary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
                .itmoTint()
                .accessibilityIdentifier("map.route")
                Button { actions.routeFrom() } label: {
                    Text(verbatim: AppStrings.string("map_route_from_here")).frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .itmoTint()
                .accessibilityIdentifier("map.routeFrom")
            }
            .padding(.horizontal, ItmoSpacing.group)
            .padding(.top, ItmoSpacing.compact)
        }
        .accessibilityIdentifier("map.room")
    }

    /// The route: its start and end in one line with the swap (either end opens the search to pick it again), then the shown
    /// step with its icon, the progress and the arrows; while picking an end, the search's results instead.
    private func tripCard(_ trip: TripView) -> some View {
        sheet {
            HStack(spacing: ItmoSpacing.related) {
                endpoint(.from, text: trip.from, trip: trip)
                Button { actions.swap() } label: {
                    AppSymbol.swapVert.image.rotationEffect(.degrees(90)).frame(width: 44, height: 44).contentShape(Rectangle())
                }
                    .buttonStyle(.plain)
                    .disabled(trip.to == nil)
                    .accessibilityLabel(AppStrings.string("map_route_swap"))
                endpoint(.to, text: trip.to, trip: trip)
                closeButton(actions.closeTrip)
            }
            .padding(.horizontal, ItmoSpacing.compact)
            if trip.picking != nil {
                if plan.search.results.isEmpty, plan.search.elsewhere.isEmpty {
                    hint(plan.search.query.isEmpty ? "map_route_pick_hint" : "map_search_empty")
                } else {
                    Divider().padding(.top, ItmoSpacing.related)
                    results(maxHeight: 220)
                }
            } else if trip.notFound {
                hint("map_route_not_found")
            } else if let step = trip.step {
                stepView(step, trip: trip)
            } else {
                hint("map_route_pick_hint")
            }
        }
        .accessibilityIdentifier("map.trip")
    }

    private func endpoint(_ end: RouteEnd, text: UiText?, trip: TripView) -> some View {
        let picking = trip.picking == end
        let placeholder = AppStrings.string(end == .from ? "map_route_pick_from" : "map_route_pick_to")
        return Button { actions.pick(end) } label: {
            HStack(spacing: ItmoSpacing.compact) {
                Text(verbatim: AppStrings.string(end == .from ? "map_route_badge_from" : "map_route_badge_to"))
                    .font(.itmo(.labelSmall, emphasized: true))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(width: 20, height: 20)
                    .background(ItmoColor.primary, in: Circle())
                Text(verbatim: text?.resolved ?? placeholder)
                    .font(.itmo(.titleSmall))
                    .foregroundStyle(picking ? ItmoColor.onPrimaryContainer : (text == nil ? ItmoColor.onSurfaceVariant : ItmoColor.onSurface))
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, ItmoSpacing.compact)
            .frame(maxWidth: .infinity, minHeight: 40)
            .background(picking ? ItmoColor.primaryContainer : ItmoColor.surfaceContainerHighest, in: .capsule)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityHint(Text(verbatim: placeholder))
        .accessibilityIdentifier(end == .from ? "map.end.from" : "map.end.to")
    }

    private func stepView(_ step: TripStep, trip: TripView) -> some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            HStack(spacing: 4) {
                ForEach(0..<trip.steps.count, id: \.self) { index in
                    Capsule()
                        .fill(index <= Int(trip.current) ? ItmoColor.primary : ItmoColor.outlineVariant)
                        .frame(height: 4)
                }
            }
            .padding(.horizontal, ItmoSpacing.group)
            .accessibilityHidden(true)
            HStack(spacing: ItmoSpacing.content) {
                MapSprite(name: step.kind.icon, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: "\(step.counter.resolved) · \(step.title.resolved)")
                        .font(.itmo(.labelMedium))
                        .foregroundStyle(.secondary)
                    Text(verbatim: step.text.resolved).font(.itmo(.titleSmall, emphasized: true)).lineLimit(2)
                }
                Spacer(minLength: 0)
                if let destination = step.destination {
                    iconButton(.openInNew, label: "map_open_in_maps") { actions.openMaps(destination) }
                }
                iconButton(.chevronLeft, label: "map_route_previous") { actions.step(trip.current - 1) }
                    .disabled(trip.current == 0)
                iconButton(.chevronRight, label: "map_route_next") { actions.step(trip.current + 1) }
                    .disabled(Int(trip.current) >= trip.steps.count - 1)
                    .accessibilityIdentifier("map.next")
            }
            .padding(.leading, ItmoSpacing.group)
            .padding(.trailing, ItmoSpacing.related)
        }
        .padding(.top, ItmoSpacing.compact)
    }

    private func hint(_ key: String) -> some View {
        Text(verbatim: AppStrings.string(key))
            .font(.itmo(.bodyMedium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, ItmoSpacing.group)
            .padding(.vertical, ItmoSpacing.compact)
    }

    @ViewBuilder private func results(maxHeight: CGFloat) -> some View {
        if plan.search.empty {
            hint("map_search_empty")
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(plan.search.results, id: \.roomId) { resultRow($0) }
                    if !plan.search.elsewhere.isEmpty {
                        Text(verbatim: AppStrings.string("map_search_other_buildings"))
                            .font(.itmo(.labelLarge, emphasized: true))
                            .foregroundStyle(ItmoColor.primary)
                            .padding(.horizontal, ItmoSpacing.group)
                            .padding(.vertical, ItmoSpacing.compact)
                        ForEach(plan.search.elsewhere, id: \.self) { resultRow($0) }
                    }
                }
            }
            .frame(maxHeight: maxHeight)
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func resultRow(_ result: SearchResult) -> some View {
        Button {
            endSearch()
            actions.result(result)
        } label: {
            HStack(spacing: ItmoSpacing.content) {
                MapSprite(name: result.icon, size: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: result.title.resolved).font(.itmo(.bodyLarge)).lineLimit(1)
                    Text(verbatim: result.subtitle.resolved).font(.itmo(.bodySmall)).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, ItmoSpacing.group)
            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("map.result")
    }

    private func iconButton(_ symbol: AppSymbol, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { symbol.image.frame(width: 44, height: 44).contentShape(Rectangle()) }
            .buttonStyle(.plain)
            .foregroundStyle(ItmoColor.primary)
            .accessibilityLabel(AppStrings.string(label))
    }

    private func closeButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) { AppSymbol.close.image.frame(width: 44, height: 44).contentShape(Rectangle()) }
            .buttonStyle(.plain)
            .accessibilityLabel(AppStrings.string("map_close"))
            .accessibilityIdentifier("map.close")
    }
}

private extension View {
    /// The shadow of everything over the plan, as itmo-map-web's floating controls.
    func mapChrome() -> some View {
        shadow(color: .black.opacity(0.12), radius: 8, y: 2)
    }

    /// A chip over the plan: an opaque capsule, the accent's container when selected.
    func mapChip(selected: Bool) -> some View {
        font(.itmo(.labelLarge))
            .lineLimit(1)
            .padding(.leading, 6)
            .padding(.trailing, 12)
            .frame(minHeight: 36)
            .foregroundStyle(selected ? ItmoColor.onPrimaryContainer : ItmoColor.onSurface)
            .background(selected ? ItmoColor.primaryContainer : ItmoColor.surface, in: .capsule)
            .contentShape(.capsule)
            .mapChrome()
    }
}

/// One of the campus app's map icons (`CampusMapStyle.SPRITES`, the bundle's `map_sprites`), drawn for a pixel
/// ratio of 3, as itmo-map-web shows them in its lists.
struct MapSprite: View {
    let name: String
    let size: CGFloat

    var body: some View {
        Group {
            if let image = Self.image(name) {
                Image(uiImage: image).resizable().interpolation(.high)
            } else {
                Color.clear
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    @MainActor private static var cache: [String: UIImage] = [:]

    @MainActor static func image(_ name: String) -> UIImage? {
        if let image = cache[name] { return image }
        guard let url = Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "map_sprites"),
              let data = try? Data(contentsOf: url),
              let image = UIImage(data: data, scale: CGFloat(CampusMapStyle.shared.SPRITE_PIXEL_RATIO)) else { return nil }
        cache[name] = image
        return image
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
