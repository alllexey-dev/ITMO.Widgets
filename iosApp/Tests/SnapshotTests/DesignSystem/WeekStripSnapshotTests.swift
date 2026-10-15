import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

@MainActor
final class WeekStripSnapshotTests: XCTestCase {
    func testPastTodayAndSelectedDays() {
        let view = VStack(spacing: ItmoSpacing.content) {
            strip(selected: 2)
            strip(selected: 0)
            strip(selected: 4)
        }
        .padding(ItmoSpacing.screenMargin)
        assertAppearances(of: view, named: "week", appearances: [.light, .dark])
    }

    private func strip(selected: Int) -> some View {
        let weekdays = ["пн", "вт", "ср", "чт", "пт", "сб", "вс"]
        let days = weekdays.indices.map { index in
            ItmoWeekDay(
                id: "day-\(index)", weekday: weekdays[index], number: "\(index + 7)", lessons: 3,
                changed: index == 1, today: index == 2, past: index < 2, selected: index == selected,
                label: "\(weekdays[index]) \(index + 7)"
            )
        }
        return ItmoWeekStrip(days: days) { _ in }
    }
}
