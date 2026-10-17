import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Расписание in the shell (the static root header with the month menu and the friends button, the week strip, the
/// feed) on the shared `ScheduleFeedUiState` that the real presenter builds from the synthetic weeks of
/// `ScheduleSamples` (Monday 7 September 2026, 12:22), its states, a friend's week, and the schedule's sheets: the
/// month, the friend picker and the lesson sheet. Drawn in a window, 375 pt wide.
@MainActor
final class ScheduleSnapshotTests: XCTestCase {
    private let samples = ScheduleSamples.shared

    /// Today with a changed room, the lesson going on, a window, an online lesson and a booked swim; a long subject
    /// with a long room name on Wednesday.
    func testToday() {
        assertFeed(samples.Feed, named: "today")
    }

    /// Monday and Tuesday seen on Wednesday: their day headers are dimmed as past.
    func testPastDays() {
        assertFeed(samples.PastDays, named: "past-days", appearances: [.light, .dark])
    }

    /// The next week: `Сегодня` in the header.
    func testNextWeek() {
        assertFeed(samples.NextWeek, named: "next-week", appearances: [.light, .dark])
    }

    /// A friend's week: `Вы` on the shared lessons and `Вернуться к своему`.
    func testFriend() {
        assertFeed(samples.FriendFeed, named: "friend")
    }

    /// Weeks without a lesson: short `пар нет` rows and collapsed weekends.
    func testEmpty() {
        assertFeed(samples.Empty, named: "empty", appearances: [.light, .dark])
    }

    func testFailed() {
        assertFeed(samples.Failed, named: "failed", appearances: [.light, .dark])
    }

    /// Two lessons at once: one now marker, a monotonic rail and the later one's `одновременно с` note.
    func testOverlap() {
        assertFeed(samples.Overlap, named: "overlap")
    }

    func testMonth() {
        assertSheet(named: "month", title: "Переход к дате") { ScheduleMonthContent(state: self.samples.Month) }
    }

    func testMonthFreeDay() {
        assertSheet(named: "month-free", title: "Переход к дате", appearances: [.light, .dark]) {
            ScheduleMonthContent(state: self.samples.MonthFreeDay)
        }
    }

    func testFriendPicker() {
        assertSheet(named: "picker", title: "Чьё расписание") { FriendPickerContent(state: self.samples.Picker) }
    }

    func testFriendPickerGate() {
        assertSheet(named: "picker-gate", title: "Чьё расписание", appearances: [.light, .dark]) {
            FriendPickerContent(state: self.samples.PickerDisabled)
        }
    }

    func testLessonNow() {
        assertSheet(named: "lesson-now", title: "Лабораторная, пн, 7 сентября") {
            LessonSheetContent(state: self.samples.SheetNow)
        }
    }

    /// An online lesson: `Онлайн` big, the meeting and its password; at AX1 the time goes under the word.
    func testLessonOnline() {
        assertSheet(named: "lesson-online", title: "Практика, пн, 7 сентября") {
            LessonSheetContent(state: self.samples.SheetOnline)
        }
    }

    /// A changed lesson with a long room name and a long teacher's name.
    func testLessonChanged() {
        assertSheet(named: "lesson-changed", title: "Практика, ср, 9 сентября") {
            LessonSheetContent(state: self.samples.SheetChanged)
        }
    }

    func testLessonSport() {
        assertSheet(named: "lesson-sport", title: "Спорт, вт, 8 сентября", appearances: [.light, .dark]) {
            LessonSheetContent(state: self.samples.SheetSport)
        }
    }

    private func assertFeed(
        _ state: ScheduleFeedUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let screen = ShellTabs(selection: .constant(.schedule), openPass: {}) { _ in
            NavigationStack {
                ScheduleFeedContent(state: state)
                    .itmoRootHeader(
                        title: state.title.resolved,
                        subtitle: state.subtitle?.resolved,
                        titleMenu: state.monthMenu ? {} : nil
                    ) {
                        if state.offWeek {
                            Button {} label: { AppSymbol.arrowUpward.image.frame(width: 28, height: 28) }
                                .buttonStyle(.glass)
                                .buttonBorderShape(.circle)
                        }
                        Button {} label: { AppSymbol.group.image.frame(width: 28, height: 28) }
                            .buttonStyle(.glass)
                            .buttonBorderShape(.circle)
                    }
                    .overlay(alignment: .bottom) {
                        if state.friend != nil {
                            ItmoProminentButton(title: "Вернуться к своему", symbol: .undo) {}.fixedSize()
                        }
                    }
            }
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, settle: 1.5, file: file, testName: testName, line: line
        )
    }

    private func assertSheet<Content: View>(
        named name: String,
        title: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        @ViewBuilder content: @escaping () -> Content
    ) {
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) {
            ItmoSheet(title: title, height: .full) { content() }
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, settle: 2.5, file: file, testName: testName, line: line
        )
    }
}
