@testable import ITMOWidgets
import Shared
import XCTest

/// The sign-in and first-run wiring on the app's graph: the screens' ViewModels resolve from `App.init`'s
/// Koin graph (the first-run flow with its `SavedStateHandle` parameter), the flow walks its two steps, and the gate's flag comes from `OnboardingGateViewModel`. Nothing here completes the flow or
/// changes the stored choices.
@MainActor
final class OnboardingTests: XCTestCase {
    func testSignInViewModelsResolveFromTheAppGraph() {
        let auth = ObservableViewModel(AuthViewModel.self, state: \.uiState)
        XCTAssertNotNil(auth.state)
        let login = ObservableViewModel(InteractiveLoginViewModel.self, state: \.uiState)
        XCTAssertEqual(login.state.page, .loading)
    }

    func testFlowWalksItsTwoSteps() {
        let model = OnboardingScreen.makeModel()
        XCTAssertEqual(model.state.step, .permissions)
        XCTAssertEqual(model.state.stepCount, 2)
        XCTAssertFalse(model.state.finished)

        model.viewModel.next()
        XCTAssertEqual(model.state.step, .connection)
        model.viewModel.back()
        XCTAssertEqual(model.state.step, .permissions)
        model.viewModel.back()
        XCTAssertEqual(model.state.step, .permissions, "the first step has nothing before it")
    }

    func testEachScreenGetsItsOwnFlow() {
        let first = OnboardingScreen.makeModel()
        let second = OnboardingScreen.makeModel()
        first.viewModel.next()
        XCTAssertEqual(second.state.step, .permissions)
        XCTAssertFalse(first.viewModel === second.viewModel)
    }

    func testGatewayReportsTheFirstRunFlag() async {
        let gateway = SharedSessionGateway.fromGraph()
        var iterator = gateway.onboardingStates().makeAsyncIterator()
        let first = await iterator.next()
        XCTAssertNotNil(first)
    }
}
