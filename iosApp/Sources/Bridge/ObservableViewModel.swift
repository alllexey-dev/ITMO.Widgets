import Observation
import Shared
import SwiftUI

/// The base class of every shared ViewModel, as the `Shared` framework names it.
typealias SharedViewModel = Lifecycle_viewmodelViewModel

/// A shared ViewModel as SwiftUI observes it.
///
/// - Owns a `ScreenViewModelStore`. The ViewModel is created on first use of `viewModel` or `state`, and `deinit`
///   clears the store, which runs `onCleared` and cancels `viewModelScope`; nothing else does that on iOS.
/// - `state` is the ViewModel's `uiState`, kept current while a view runs `observing(_:)`; `onEvents(of:_:perform:)`
///   delivers its `events`.
/// - Hold it in `@State`. SwiftUI evaluates a `@State` initial value each time it recreates the view and keeps only
///   the first, so construction resolves nothing: a discarded instance never creates a ViewModel.
@MainActor
@Observable
final class ObservableViewModel<Model: SharedViewModel, State: AnyObject> {
    @ObservationIgnored private let store = ScreenViewModelStore()
    @ObservationIgnored private let make: (ScreenViewModelStore) -> Model
    @ObservationIgnored private let uiState: KeyPath<Model, SkieSwiftStateFlow<State>>
    @ObservationIgnored private var instance: Model?

    /// The last value the observing task received; nil until the first.
    private var latest: State?

    /// The ViewModel of `type` from the app's Koin graph (`viewModelOf` in the feature's module).
    convenience init(_ type: Model.Type, state: KeyPath<Model, SkieSwiftStateFlow<State>>) {
        self.init(state: state) { store in
            guard let model = store.resolve(type: type) as? Model else {
                preconditionFailure("Koin resolved no \(Model.self)")
            }
            return model
        }
    }

    /// A ViewModel that `make` builds into the store (`ScreenViewModelStore.getOrCreate`), for one outside the graph.
    init(state: KeyPath<Model, SkieSwiftStateFlow<State>>, make: @escaping (ScreenViewModelStore) -> Model) {
        self.uiState = state
        self.make = make
    }

    var viewModel: Model {
        if let instance { return instance }
        let created = make(store)
        instance = created
        return created
    }

    var state: State {
        latest ?? viewModel[keyPath: uiState].value
    }

    /// Follows `uiState` until the calling task is cancelled; `observing(_:)` runs it in the view's task.
    func observe() async {
        for await value in viewModel[keyPath: uiState] {
            latest = value
        }
    }

    /// A pull-to-refresh for `.refreshable`, whose spinner stays exactly while its action runs: `start` asks the
    /// ViewModel for the refresh (`refresh(mode: .pull)`), and this returns once `refreshing` reads false, so a pull
    /// that joins a refresh in flight waits for it too. The state follows `start` a moment later and a cached answer
    /// is instant, so the spinner stays at least `PullRefresh.minimum`.
    func pullRefresh(_ start: (Model) -> Void, while refreshing: @escaping (Model) -> Bool) async {
        start(viewModel)
        let model = viewModel
        // SwiftUI cancels the refresh action once the state it shows changes, and a refresh always changes it: the
        // wait runs outside that task, bounded by `PullRefresh.maximum` for a ViewModel cleared mid-refresh.
        await Task { @MainActor in
            let started = ContinuousClock.now
            while ContinuousClock.now - started < PullRefresh.maximum {
                let waited = ContinuousClock.now - started
                if waited >= PullRefresh.minimum && !refreshing(model) { return }
                try? await Task.sleep(for: .milliseconds(100))
            }
        }.value
    }

    deinit {
        store.clear()
    }
}

enum PullRefresh {
    /// The launch argument of a Debug build that keeps a pull's spinner five seconds, so a UI test sees it over the
    /// demo's instant refresh.
    static let slowArgument = "-itmoSlowRefresh"

    static let minimum: Duration = {
        #if DEBUG
        if CommandLine.arguments.contains(slowArgument) { return .seconds(5) }
        #endif
        return .milliseconds(500)
    }()

    /// A cleared ViewModel's state stops changing; the spinner never outlasts this.
    static let maximum: Duration = .seconds(60)
}

extension View {
    /// Keeps `model.state` current while this view is on screen.
    func observing<Model, State>(_ model: ObservableViewModel<Model, State>) -> some View {
        task { await model.observe() }
    }

    /// Delivers each of the ViewModel's one-shot `events` to `perform` while this view is on screen; an event sent
    /// while no view collects waits for the next collector (`EventQueue`). Switch over `onEnum(of: event)`.
    func onEvents<Model, State, Event>(
        of model: ObservableViewModel<Model, State>,
        _ events: KeyPath<Model, SkieSwiftFlow<Event>>,
        perform: @escaping @MainActor (Event) -> Void
    ) -> some View {
        task {
            for await event in model.viewModel[keyPath: events] {
                perform(event)
            }
        }
    }
}
