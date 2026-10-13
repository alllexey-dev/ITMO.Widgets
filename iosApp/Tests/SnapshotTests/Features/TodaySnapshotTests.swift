import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Сегодня in the shell (the root header with the bell and its badge, the glass tab bar with the pass accessory) on
/// the shared `TodayUiState` that the real presenter builds from the synthetic Monday of `TodaySamples` at the design
/// lab's moments, and its states. Drawn in a window, 375 pt wide.
@MainActor
final class TodaySnapshotTests: XCTestCase {
    private let samples = TodaySamples.shared

    func testNow() {
        assertToday(samples.at(time: "12:22", todos: [], refresh: TodayRefreshDone.shared), named: "now")
    }

    func testMorning() {
        assertToday(samples.at(time: "08:05", todos: [], refresh: TodayRefreshDone.shared), named: "morning")
    }

    func testOnline() {
        assertToday(
            samples.at(time: "15:30", todos: [], refresh: TodayRefreshDone.shared), named: "online",
            appearances: [.light, .dark]
        )
    }

    /// Tomorrow in the evening, with the BARS sign-in under it; scrolled to the end, where it sits.
    func testEveningWithAThingToDo() {
        let state = samples.at(time: "19:40", todos: [samples.BarsTodo], refresh: TodayRefreshDone.shared)
        assertToday(state, named: "evening", scrolled: true)
    }

    func testStale() {
        let state = samples.at(time: "10:30", todos: [], refresh: TodayRefreshFailed(error: AppErrorNetwork.shared))
        assertToday(state, named: "stale", appearances: [.light, .dark])
    }

    func testEmpty() {
        assertToday(samples.Empty, named: "empty", appearances: [.light, .dark])
    }

    func testFailed() {
        assertToday(samples.Failed, named: "failed", appearances: [.light, .dark])
    }

    private func assertToday(
        _ state: TodayUiState,
        named name: String,
        scrolled: Bool = false,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let screen = ShellTabs(selection: .constant(.today), openPass: {}) { _ in
            NavigationStack {
                TodayContent(state: state)
                    .defaultScrollAnchor(scrolled ? .bottom : .top)
                    .itmoRootHeader(title: "Сегодня", subtitle: "понедельник, 7 сентября")
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            ItmoBellButton(label: "Уведомления", badge: 3) {}
                        }
                    }
            }
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, settle: 1, file: file, testName: testName, line: line
        )
    }
}
