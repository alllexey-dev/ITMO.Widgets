@testable import ITMOWidgets
import Shared
import XCTest

/// The sign-in and first-run wiring on the app's graph (IO-07b): the screens' ViewModels resolve from `App.init`'s
/// Koin graph (the first-run flow with its `SavedStateHandle` parameter), the flow reads the stored widget appearance
/// and walks its steps, and the gate's flag comes from `OnboardingGateViewModel`. Nothing here completes the flow or
/// changes the stored choices.
@MainActor
final class OnboardingTests: XCTestCase {
    func testSignInViewModelsResolveFromTheAppGraph() {
        let auth = ObservableViewModel(AuthViewModel.self, state: \.uiState)
        XCTAssertNotNil(auth.state)
        let login = ObservableViewModel(InteractiveLoginViewModel.self, state: \.uiState)
        XCTAssertEqual(login.state.page, .loading)
    }

    func testFlowReadsTheAppearanceAndWalksItsSteps() async {
        let model = OnboardingScreen.makeModel()
        XCTAssertEqual(model.state.step, .compactWidget)
        XCTAssertFalse(model.state.finished)

        await waitUntil { model.state.appearance != nil }
        model.viewModel.onPinSupportChanged(supported: false)
        XCTAssertFalse(model.state.pinSupported)

        model.viewModel.next()
        XCTAssertEqual(model.state.step, .fullWidget)
        model.viewModel.back()
        XCTAssertEqual(model.state.step, .compactWidget)
        model.viewModel.back()
        XCTAssertEqual(model.state.step, .compactWidget, "the first step has nothing before it")
    }

    func testEachScreenGetsItsOwnFlow() {
        let first = OnboardingScreen.makeModel()
        let second = OnboardingScreen.makeModel()
        first.viewModel.next()
        XCTAssertEqual(second.state.step, .compactWidget)
        XCTAssertFalse(first.viewModel === second.viewModel)
    }

    func testGatewayReportsTheFirstRunFlag() async {
        let gateway = SharedSessionGateway.fromGraph()
        var iterator = gateway.onboardingStates().makeAsyncIterator()
        let first = await iterator.next()
        XCTAssertNotNil(first)
    }

    func testWidgetOptionsFollowTheSharedRows() {
        XCTAssertEqual(WidgetKind.singleLesson.widgetOptions, [.compactNextLessonEarly, .compactHideTeacher])
        XCTAssertEqual(
            WidgetKind.daySchedule.widgetOptions,
            [.fullHideTeacher, .fullHidePastLessons, .fullShowTomorrow]
        )
        XCTAssertEqual(WidgetKind.qr.widgetOptions, [.qrDynamicColors, .qrSpoiler])
        XCTAssertNil(WidgetKind.qr.textSize(in: OnboardingFixtures.appearance))
        XCTAssertEqual(WidgetKind.daySchedule.textSize(in: OnboardingFixtures.appearance), .large)
        XCTAssertTrue(WidgetOption.qrSpoiler.isEnabled(in: OnboardingFixtures.appearance))
        XCTAssertFalse(WidgetOption.fullHidePastLessons.isEnabled(in: OnboardingFixtures.appearance))
    }

    private func waitUntil(timeout: TimeInterval = 5, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(condition(), "the condition did not hold within \(timeout) s")
    }
}

/// A widget appearance away from every default, for the tests of the rows.
enum OnboardingFixtures {
    static let appearance = WidgetAppearance(
        schedule: ScheduleWidgetSettings(
            compact: CompactScheduleWidgetSettings(showNextLessonEarly: false, hideTeacher: true, textSize: .normal),
            full: FullScheduleWidgetSettings(
                hideTeacher: false,
                hidePastLessons: false,
                showTomorrowWhenTodayIsOver: true,
                textSize: .large
            )
        ),
        qr: QrWidgetSettings(dynamicColors: true, spoilerEnabled: true, animationType: .circle)
    )
}
