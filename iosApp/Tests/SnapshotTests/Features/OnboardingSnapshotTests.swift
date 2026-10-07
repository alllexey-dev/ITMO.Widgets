import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The first-run flow (IO-07b) in all four appearances, one reference per step state Android's `OnboardingScreen`
/// references cover: a schedule widget and the QR widget with their how-to and rows, rows waiting for the stored
/// appearance, the opt-in on and running, and the notification step before and after the question.
/// `OnboardingContent` is rendered directly, without the ViewModel.
@MainActor
final class OnboardingSnapshotTests: XCTestCase {
    private let screenHeight: CGFloat = 640

    func testCompactWidget() {
        assertAppearances(of: content(.compactWidget), named: "widget-single-lesson", height: screenHeight)
    }

    func testQrWidget() {
        assertAppearances(of: content(.qrWidget), named: "widget-qr", height: screenHeight)
    }

    func testWidgetRowsWaitForTheStoredAppearance() {
        assertAppearances(of: content(.fullWidget, appearance: nil), named: "widget-loading", height: screenHeight)
    }

    func testServicesOn() {
        assertAppearances(of: content(.services, servicesEnabled: true), named: "services-on", height: screenHeight)
    }

    func testServicesBusy() {
        assertAppearances(
            of: content(.services, servicesEnabled: false, servicesBusy: true),
            named: "services-busy",
            height: screenHeight
        )
    }

    func testNotificationsAsk() {
        assertAppearances(of: content(.notifications), named: "notifications-ask", height: screenHeight)
    }

    func testNotificationsGranted() {
        assertAppearances(
            of: content(.notifications, notificationsGranted: true, notificationsAsked: true),
            named: "notifications-granted",
            height: screenHeight
        )
    }

    func testNotificationsDenied() {
        assertAppearances(
            of: content(.notifications, notificationsAsked: true),
            named: "notifications-denied",
            height: screenHeight
        )
    }

    private func content(
        _ step: OnboardingStep,
        appearance: WidgetAppearance? = OnboardingSnapshotFixtures.appearance,
        servicesEnabled: Bool = true,
        servicesBusy: Bool = false,
        notificationsGranted: Bool = false,
        notificationsAsked: Bool = false
    ) -> some View {
        let state = OnboardingUiState(
            step: step,
            pinSupported: false,
            pinnedWidgets: [],
            appearance: appearance,
            servicesEnabled: servicesEnabled,
            servicesBusy: servicesBusy,
            notificationsGranted: notificationsGranted,
            notificationsAsked: notificationsAsked,
            customSpoiler: nil,
            spoilerBusy: false,
            spoilerRevision: 0,
            finished: false
        )
        return OnboardingContent(state: state, actions: OnboardingActions())
    }
}

/// A widget appearance away from the defaults, so the references show both switch states and a changed text size.
enum OnboardingSnapshotFixtures {
    static let appearance = WidgetAppearance(
        schedule: ScheduleWidgetSettings(
            compact: CompactScheduleWidgetSettings(showNextLessonEarly: true, hideTeacher: false, textSize: .large),
            full: FullScheduleWidgetSettings(
                hideTeacher: true,
                hidePastLessons: false,
                showTomorrowWhenTodayIsOver: true,
                textSize: .normal
            )
        ),
        qr: QrWidgetSettings(dynamicColors: true, spoilerEnabled: false, animationType: .circle)
    )
}
