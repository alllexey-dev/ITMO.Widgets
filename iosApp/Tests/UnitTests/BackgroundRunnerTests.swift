@testable import ITMOWidgets
import Shared
import XCTest

/// The app refresh task on the app's graph (IO-14). The system's scheduler cannot be forced from a test, so these run
/// the entry point the task runs and the Debug trigger's fixture post; the runner's rules (order, deadline, period,
/// retries) are `BackgroundRunnerTest` in `shared/ios`.
final class BackgroundRunnerTests: XCTestCase {

    func testTheTaskIsPermittedAndBackgroundFetchIsOn() throws {
        let info = try XCTUnwrap(Bundle.main.infoDictionary)
        XCTAssertEqual(info["BGTaskSchedulerPermittedIdentifiers"] as? [String], [BackgroundRefresh.taskIdentifier])
        XCTAssertEqual(info["UIBackgroundModes"] as? [String], ["fetch"])
    }

    func testTheEntryPointRunsWidgetSnapshotsThenScheduleChangesThenTheCalendarSync() async throws {
        let finished = await BackgroundRefresh.run()
        let report = try XCTUnwrap(finished)

        XCTAssertEqual(report.steps.map(\.key), ["widget-snapshots", "schedule-changes", "calendar-sync"])
        XCTAssertEqual(report.resultOf(key: "widget-snapshots"), .done)
        // The hosted tests' session has no ITMO.ID token, so the schedule data graph's checks skip.
        XCTAssertEqual(report.resultOf(key: "schedule-changes"), .skipped)
        XCTAssertEqual(report.resultOf(key: "calendar-sync"), .skipped)
    }

    func testTheDebugTriggerPostsTheFixtureChangeByCatalogKey() async throws {
        let center = RecordingNotificationCenter()

        try await postFixtureScheduleChange(center: center)

        let request = try XCTUnwrap(center.added.single)
        XCTAssertEqual(request.identifier, "schedule_changes-1")
        XCTAssertEqual(request.threadIdentifier, AppNotificationChannels.shared.SCHEDULE_CHANGES)
        XCTAssertEqual(request.title, "Расписание изменилось: 1 пара")
        XCTAssertTrue(request.body.hasPrefix("Математический анализ — отменена: "), request.body)
        XCTAssertFalse(request.silent, "a change of tomorrow makes a sound")
        XCTAssertNil(request.deliverAt)
        XCTAssertEqual(
            request.userInfo["action"] as? String,
            AppEntryIntents.shared.ACTION_OPEN_SCHEDULE_CHANGES
        )
    }

    func testTheDebugTriggerRunsOnlyOnItsArgument() {
        XCTAssertEqual(BackgroundRefresh.runArgument, "-itmoRunRefresh")
    }
}

/// The notifications the fixture post hands to the system, kept instead of posted.
private final class RecordingNotificationCenter: NSObject, LocalNotificationCenter, @unchecked Sendable {
    private let lock = NSLock()
    private var requests: [LocalNotificationRequest] = []

    var added: [LocalNotificationRequest] {
        lock.withLock { requests }
    }

    func add(request: LocalNotificationRequest) {
        lock.withLock { requests.append(request) }
    }

    func remove(identifier: String) {}

    func removeAll() {}
}

private extension Array {
    var single: Element? { count == 1 ? first : nil }
}
