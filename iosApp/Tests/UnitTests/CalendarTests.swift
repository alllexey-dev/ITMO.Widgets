import EventKit
import Shared
import UniformTypeIdentifiers
import XCTest
@testable import ITMOWidgets

/// The calendar on iOS (IO-15b): what turning the sync on asks of EventKit, the settings rows iOS now lists, the
/// `.ics` sheet's ViewModel in the app's graph and, with full access granted to the simulator
/// (`xcrun simctl privacy <udid> grant calendar dev.alllexey.itmowidgets`), the app's calendar made and removed in
/// EventKit through the shared sync. The sync's rules over a fake event store are `EventKitCalendarSyncTest`.
@MainActor
final class CalendarTests: XCTestCase {

    // MARK: Access

    func testFullAccessTurnsTheSyncOnWithoutAPrompt() async {
        let access = FakeAccess(status: .fullAccess)

        let outcome = await access.calendarAccess.turnOn()

        XCTAssertEqual(outcome, .granted)
        XCTAssertEqual(access.requests, 0)
    }

    func testNeverAskedAsksOnceAndFollowsTheAnswer() async {
        let granting = FakeAccess(status: .notDetermined, answer: true)
        let refusing = FakeAccess(status: .notDetermined, answer: false)

        let granted = await granting.calendarAccess.turnOn()
        let refused = await refusing.calendarAccess.turnOn()

        XCTAssertEqual(granted, .granted)
        XCTAssertEqual(refused, .refused)
        XCTAssertEqual(granting.requests + refusing.requests, 2)
    }

    func testWriteOnlyAccessAsksForFullAccess() async {
        let access = FakeAccess(status: .writeOnly, answer: true)

        let outcome = await access.calendarAccess.turnOn()

        XCTAssertEqual(outcome, .granted)
        XCTAssertEqual(access.requests, 1)
    }

    func testDeniedAccessShowsTheRationaleWithoutAPrompt() async {
        for status in [EKAuthorizationStatus.denied, .restricted] {
            let access = FakeAccess(status: status, answer: true)

            let outcome = await access.calendarAccess.turnOn()

            XCTAssertEqual(outcome, .rationale, "\(status.rawValue)")
            XCTAssertEqual(access.requests, 0, "iOS shows no second prompt")
        }
    }

    func testTheSystemPromptHasItsPurposeString() throws {
        let purpose = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "NSCalendarsFullAccessUsageDescription") as? String)
        XCTAssertEqual(purpose, AppStrings.string("ios_calendar_full_access_usage"))
    }

    // MARK: Settings and the .ics sheet

    func testTheSchedulePageListsTheCalendarSwitchAndTheIcsRow() async {
        let model = ObservableViewModel<SettingsViewModel, SettingsUiState>(state: \.uiState) { store in
            let parameters: [Any?] = settingsPageParameters(page: "SCHEDULE")
            return store.resolve(type: SettingsViewModel.self, parameters: parameters) as! SettingsViewModel
        }

        for await state in model.viewModel.uiState where state.loaded {
            let ids = state.sections.flatMap(\.items).map(\.id)
            XCTAssertTrue(ids.contains(.calendarSync), "\(ids)")
            XCTAssertTrue(ids.contains(.icsExport), "\(ids)")
            break
        }
    }

    func testTheIcsSheetStartsAtTheRangeChoice() throws {
        let model = ObservableViewModel<IcsExportViewModel, IcsExportUiState>(state: \.uiState) { store in
            let parameters: [Any?] = icsExportParameters()
            return store.resolve(type: IcsExportViewModel.self, parameters: parameters) as! IcsExportViewModel
        }

        let choice = try XCTUnwrap(model.state as? IcsExportUiStateChoose)
        XCTAssertEqual(choice.options.map(\.kind), [.week, .twoWeeks, .semester, .custom])
    }

    func testAPickedDayIsTheDayThePickerShowed() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Vladivostok"))
        let lateEvening = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 5, hour: 23)))

        XCTAssertEqual(IcsDay.iso(lateEvening, calendar: calendar), "2026-10-05")
    }

    func testTheWrittenFileIsSharedAsACalendarEvent() {
        XCTAssertEqual(IcsDocument.contentType.preferredMIMEType, "text/calendar")
        XCTAssertTrue(IcsDocument.contentType.conforms(to: .calendarEvent))
    }

    // MARK: EventKit on the simulator

    func testTurningOnMakesTheAppsCalendarAndTurningOffRemovesIt() async throws {
        try XCTSkipUnless(
            EKEventStore.authorizationStatus(for: .event) == .fullAccess,
            "grant the simulator calendar access first (simctl privacy grant calendar)"
        )
        let sync = IosKoin.shared.get(protocol: CalendarSync.self) as! CalendarSync

        let result = try await sync.enable()
        try XCTSkipIf(result == .demoUnavailable, "the hosted session is the demo")
        XCTAssertEqual(result, .done)
        XCTAssertEqual(ownCalendars().count, 1)

        try await sync.disable()
        XCTAssertEqual(ownCalendars().count, 0)
    }

    private func ownCalendars() -> [EKCalendar] {
        EKEventStore().calendars(for: .event).filter { $0.title == "ITMO.Widgets" }
    }
}

/// EventKit's status and prompt, recorded.
private final class FakeAccess {
    let status: EKAuthorizationStatus
    let answer: Bool
    private(set) var requests = 0

    init(status: EKAuthorizationStatus, answer: Bool = false) {
        self.status = status
        self.answer = answer
    }

    var calendarAccess: CalendarAccess {
        CalendarAccess(
            status: { self.status },
            requestFullAccess: {
                self.requests += 1
                return self.answer
            }
        )
    }
}
