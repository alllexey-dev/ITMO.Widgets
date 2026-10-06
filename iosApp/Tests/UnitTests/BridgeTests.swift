@testable import ITMOWidgets
import Shared
import SwiftUI
import XCTest

/// The Swift bridge to the shared ViewModels (L18 IO-05): SKIE's flows and sealed enums, `ObservableViewModel`
/// (a `StateFlow` update re-renders SwiftUI, dismissal clears the ViewModel) and the Koin graph `App.init` starts.
/// `BridgeProbeViewModel` is a probe in `shared/ios` on the screen contract; no screen uses it.
@MainActor
final class BridgeTests: XCTestCase {
    // MARK: Koin

    func testAppInitStartedTheGraphOnce() throws {
        XCTAssertTrue(IosKoin.shared.isStarted)
        let reloader = try XCTUnwrap(IosKoin.shared.get(protocol: WidgetReloader.self) as? WidgetReloader)
        XCTAssertTrue(reloader is AppPlatform)

        let other = AppPlatform()
        XCTAssertFalse(startKoinIos(platform: other))

        let kept = try XCTUnwrap(IosKoin.shared.get(protocol: WidgetReloader.self) as? AppPlatform)
        XCTAssertTrue(kept === reloader as AnyObject)
        XCTAssertFalse(kept === other)
    }

    func testGraphResolvesCoreTypes() {
        XCTAssertTrue(IosKoin.shared.get(protocol: AcademicTimeProvider.self) is AcademicTimeProvider)
        XCTAssertTrue(IosKoin.shared.get(protocol: PlatformActions.self) is IosPlatformActions)
    }

    // MARK: ObservableViewModel

    func testViewModelIsCreatedOnFirstUse() {
        var created = 0
        let model = probeModel { created += 1 }
        XCTAssertEqual(created, 0)

        XCTAssertEqual(model.state.count, 0)
        model.viewModel.increment()
        XCTAssertEqual(model.state.count, 1, "without an observing task state reads uiState.value")
        XCTAssertEqual(created, 1)
    }

    func testStateUpdateNotifiesObservers() async {
        let model = probeModel()
        let task = Task { await model.observe() }
        defer { task.cancel() }

        let changed = expectation(description: "Observation reported the change")
        withObservationTracking {
            _ = model.state
        } onChange: {
            changed.fulfill()
        }
        model.viewModel.increment()

        await fulfillment(of: [changed], timeout: 5)
        await waitUntil { model.state.count == 1 }
    }

    func testStateUpdateReachesSwiftUIAndDismissalClearsTheViewModel() async throws {
        let rendered = expectation(description: "the hosted view rendered count 2")
        let cleared = expectation(description: "the ViewModel was cleared")
        var probe: BridgeProbeViewModel?
        var scopeCancelled: Bool?
        var counts: [Int] = []
        let presentation = ProbePresentation()
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        // No local copy of the view: its @State initial value would keep the model alive past the dismissal.
        window.rootViewController = UIHostingController(rootView: ProbeContainer(presentation: presentation) {
            ProbeHost(
                onCleared: { cancelled in
                    scopeCancelled = cancelled
                    cleared.fulfill()
                },
                onAppear: { probe = $0 },
                onCount: { count in
                    counts.append(count)
                    if count == 2 { rendered.fulfill() }
                }
            )
        })
        window.makeKeyAndVisible()
        defer { window.isHidden = true }

        await waitUntil { probe != nil }
        let viewModel = try XCTUnwrap(probe)
        viewModel.increment()
        viewModel.increment()
        await fulfillment(of: [rendered], timeout: 5)
        XCTAssertEqual(counts.last, 2)

        presentation.isShown = false

        await fulfillment(of: [cleared], timeout: 10)
        XCTAssertEqual(scopeCancelled, true, "viewModelScope is cancelled before onCleared")
    }

    func testDeinitClearsTheViewModel() {
        var cleared = 0
        var model: ObservableViewModel<BridgeProbeViewModel, BridgeProbeState>? = probeModel(onCleared: { _ in
            cleared += 1
        })
        _ = model?.viewModel
        model = nil
        XCTAssertEqual(cleared, 1)
    }

    func testStoreKeepsOneViewModelPerType() {
        let store = ScreenViewModelStore()
        defer { store.clear() }
        let first = store.getOrCreate(type: BridgeProbeViewModel.self) { BridgeProbeViewModel { _ in } }
        let second = store.getOrCreate(type: BridgeProbeViewModel.self) { BridgeProbeViewModel { _ in } }
        XCTAssertTrue(first === second)
    }

    // MARK: SKIE

    func testEventsArriveOnceAsSwiftEnums() async {
        let model = probeModel()
        model.viewModel.increment()
        model.viewModel.reset()

        var received: [String] = []
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case let .reached(reached): received.append("reached \(reached.count)")
            case .reset: received.append("reset")
            }
            if received.count == 2 { break }
        }

        XCTAssertEqual(received, ["reached 1", "reset"])
    }

    // MARK: Helpers

    private func probeModel(
        onCreate: @escaping () -> Void = {},
        onCleared: @escaping (Bool) -> Void = { _ in }
    ) -> ObservableViewModel<BridgeProbeViewModel, BridgeProbeState> {
        ObservableViewModel(state: \.uiState) { store in
            store.getOrCreate(type: BridgeProbeViewModel.self) {
                onCreate()
                return BridgeProbeViewModel { onCleared($0.boolValue) }
            } as! BridgeProbeViewModel
        }
    }

    private func waitUntil(timeout: TimeInterval = 5, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(condition(), "the condition did not hold within \(timeout) s")
    }
}

/// Whether `ProbeContainer` shows its screen; the test dismisses it as a navigation pop or a closed sheet would.
@MainActor
@Observable
private final class ProbePresentation {
    var isShown = true
}

private struct ProbeContainer<Content: View>: View {
    let presentation: ProbePresentation
    @ViewBuilder let content: () -> Content

    var body: some View {
        if presentation.isShown {
            content()
        }
    }
}

/// A SwiftUI screen over the probe as a real screen holds its model: in `@State`, observed in the view's task.
private struct ProbeHost: View {
    @State private var model: ObservableViewModel<BridgeProbeViewModel, BridgeProbeState>
    let onAppear: (BridgeProbeViewModel) -> Void
    let onCount: (Int) -> Void

    init(
        onCleared: @escaping (Bool) -> Void,
        onAppear: @escaping (BridgeProbeViewModel) -> Void,
        onCount: @escaping (Int) -> Void
    ) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            store.getOrCreate(type: BridgeProbeViewModel.self) {
                BridgeProbeViewModel { onCleared($0.boolValue) }
            } as! BridgeProbeViewModel
        })
        self.onAppear = onAppear
        self.onCount = onCount
    }

    var body: some View {
        Text(verbatim: "\(model.state.count)")
            .observing(model)
            .onAppear { onAppear(model.viewModel) }
            .onChange(of: model.state.count, initial: true) { _, count in onCount(Int(count)) }
    }
}
