import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The `.ics` sheet (IO-15b) in all four appearances, one reference per state of `IcsExportViewModel`, rendered
/// without the ViewModel on the days of Android's references (Friday 2 October 2026). The spinner of the preparing
/// state animates, so it is recorded at the default text size only.
@MainActor
final class CalendarSnapshotTests: XCTestCase {
    private let height: CGFloat = 460
    private let ax1Height: CGFloat = 900

    func testIcsChoose() {
        assertSheet(IcsFixtures.choose, named: "ics-choose")
    }

    func testIcsPreparing() {
        assertAppearances(
            of: sheet(IcsExportUiStatePreparing.shared), named: "ics-preparing", appearances: [.light, .dark],
            height: height
        )
    }

    func testIcsReady() {
        assertSheet(IcsFixtures.ready, named: "ics-ready")
    }

    func testIcsEmpty() {
        assertSheet(IcsExportUiStateEmpty.shared, named: "ics-empty")
    }

    func testIcsFailed() {
        assertSheet(IcsExportUiStateFailed(error: AppErrorNetwork.shared), named: "ics-failed")
    }

    private func assertSheet(
        _ state: IcsExportUiState,
        named name: String,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = sheet(state)
        assertAppearances(
            of: view, named: name, appearances: [.light, .dark], height: height,
            file: file, testName: testName, line: line
        )
        assertAppearances(
            of: view, named: name, appearances: [.lightAX1, .darkAX1], height: ax1Height,
            file: file, testName: testName, line: line
        )
    }

    private func sheet(_ state: IcsExportUiState) -> some View {
        NavigationStack {
            ScrollView {
                IcsExportContent(state: state)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(Text(verbatim: AppStrings.string("settings_ics_export_title")))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// The sheet's states with Android's sample days; the day labels are typed, since Swift has no `LocalDate`.
enum IcsFixtures {
    static let choose = IcsExportUiStateChoose(options: [
        option(.week, "ics_range_week", "2–8 октября"),
        option(.twoWeeks, "ics_range_two_weeks", "2–15 октября"),
        option(.semester, "ics_range_semester", AppStrings.string("ics_range_until", ["31 января"])),
        option(.custom, "ics_range_custom", AppStrings.string("ics_range_custom_caption")),
    ])

    static let ready = IcsExportUiStateReady(
        file: IcsFile(uri: "file:///tmp/ics/schedule.ics", name: "schedule.ics", lessons: 23),
        dates: UiTextDynamic(value: "2–8 октября")
    )

    private static func option(_ kind: IcsRangeKind, _ title: String, _ dates: String) -> IcsRangeOption {
        IcsRangeOption(kind: kind, title: UiTextDynamic(value: AppStrings.string(title)), dates: UiTextDynamic(value: dates))
    }
}
