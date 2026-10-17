import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The shell in all four appearances, drawn in a window with its system chrome: the glass tab bar with the pass
/// accessory on today only, the static root header (title and subtitle with the bell in one row),
/// a pushed screen that keeps the tab bar, the three sheet heights, the signed-out gate and the damaged-link sheet.
/// The tab roots are kit fixtures: the roots of the app are still Compose screens a snapshot misses (Metal), checked
/// by the UITests (`ShellUITests` and the tab classes); Сегодня has its own (`TodaySnapshotTests`).
@MainActor
final class ShellSnapshotTests: XCTestCase {
    func testTodayTab() {
        assertScreenAppearances(of: tabs(.today), named: "today")
    }

    func testTodayWithoutThePassAccessory() {
        let shell = ShellTabs(selection: .constant(.today), showPass: false, openPass: {}) { tab in
            NavigationStack { ShellFixtureRoot(tab: tab) }
        }
        assertScreenAppearances(of: shell, named: "today-no-pass", appearances: [.light, .dark])
    }

    /// The pass accessory and the bell belong to today only.
    func testOtherTab() {
        assertScreenAppearances(of: tabs(.schedule), named: "schedule")
    }

    func testPushedScreenKeepsTheTabBar() {
        let shell = ShellTabs(selection: .constant(.recordbook), openPass: {}) { tab in
            NavigationStack(path: .constant(["subject"])) {
                ShellFixtureRoot(tab: tab)
                    .navigationDestination(for: String.self) { _ in
                        ItmoScreenStack {
                            ItmoSegmentedChoice(
                                label: "Раздел", options: ["Баллы", "Пары", "Материалы"], selection: .constant("Баллы")
                            ) { $0 }
                            ItmoLessonCard(lesson: KitFixtures.day(at: 12 * 60 + 22)[2])
                        }
                        // A long title wraps in full under the bar.
                        .itmoDetailHeader(
                            title: "Математический анализ и дифференциальные уравнения в частных производных",
                            subtitle: "Экзамен, 3 семестр"
                        )
                    }
            }
        }
        assertScreenAppearances(of: shell, named: "pushed")
    }

    func testSheetContentHeight() {
        assertScreenAppearances(of: sheet(.content), named: "content", settle: 2.5)
    }

    func testSheetTall() {
        assertScreenAppearances(of: sheet(.tall), named: "tall", settle: 2.5)
    }

    func testSheetFull() {
        assertScreenAppearances(of: sheet(.full), named: "full", settle: 2.5)
    }

    func testSignedOutGate() {
        let shell = ShellView(router: AppRouter(), session: ShellSession(state: .signedOut))
        assertScreenAppearances(of: shell, named: "gate")
    }

    func testLinkUnavailableSheet() {
        let shell = tabs(.today).sheet(isPresented: .constant(true)) { ShellSheetView(sheet: .linkUnavailable) }
        assertScreenAppearances(of: shell, named: "sheet", settle: 2.5)
    }

    // MARK: Fixtures

    private func tabs(_ selection: ShellTab) -> some View {
        ShellTabs(selection: .constant(selection), openPass: {}) { tab in
            NavigationStack {
                ShellFixtureRoot(tab: tab)
            }
        }
    }

    private func sheet(_ height: ItmoSheetHeight) -> some View {
        tabs(.today).sheet(isPresented: .constant(true)) {
            ItmoSheet(title: "Пропуск", height: height) {
                ItmoSectionHeader(title: "Пары", count: "2 пары")
                ItmoLessonCard(lesson: KitFixtures.day(at: 8 * 60)[0])
                ItmoLessonCard(lesson: KitFixtures.day(at: 8 * 60)[1])
            }
        }
    }
}

/// A tab root drawn from the kit: today has the root header with the bell over the day's timeline, the other tabs
/// their title over an empty state.
private struct ShellFixtureRoot: View {
    let tab: ShellTab

    var body: some View {
        if tab == .today {
            ItmoScreenStack {
                ItmoSectionHeader(title: "Пары", count: "5 пар")
                ItmoLessonTimeline(
                    rows: KitFixtures.rows(KitFixtures.day(at: 12 * 60 + 22), now: 12 * 60 + 22), showsNow: true
                )
            }
            .itmoRootHeader(title: tab.title, subtitle: "понедельник, 7 сентября") {
                ItmoBellButton(label: "Уведомления") {}
            }
        } else {
            ItmoScreenStack {
                ItmoEmptyView(symbol: tab.symbol, title: tab.title)
            }
            .itmoRootHeader(title: tab.title, subtitle: nil)
        }
    }
}
