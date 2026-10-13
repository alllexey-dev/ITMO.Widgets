import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The sport tab in the shell (the static root header, the glass tab bar) on the shared states the real presenters
/// build from Мария's synthetic Monday of `SportSamples`, at the design lab's axes; the lesson, filters and queue
/// sheets; `Мои секции` and `Откуда баллы` under the native bar. Drawn in a window, 375 pt wide.
@MainActor
final class SportSnapshotTests: XCTestCase {
    private let samples = SportSamples.shared

    func testMine() {
        assertSport(.my, my: samples.my(state: "active"), named: "my")
    }

    func testMineEmpty() {
        assertSport(.my, my: samples.my(state: "none"), named: "my-none", appearances: [.light, .dark])
    }

    func testSignUp() {
        assertSport(.sign, sign: samples.sign(filters: "none", day: "normal"), named: "sign")
    }

    func testSignUpFiltered() {
        assertSport(.sign, sign: samples.sign(filters: "active", day: "normal"), named: "sign-filtered", appearances: [.light, .dark])
    }

    func testSignUpEmptyDay() {
        assertSport(.sign, sign: samples.sign(filters: "none", day: "empty"), named: "sign-empty", appearances: [.light, .dark])
    }

    func testLessonWithAClash() {
        assertSheet(SportLessonSheetContent(sheet: samples.lesson(offer: "clash")), named: "lesson-clash")
    }

    func testLessonToBook() {
        assertSheet(SportLessonSheetContent(sheet: samples.lesson(offer: "can")), named: "lesson-can", appearances: [.light, .dark])
    }

    func testLessonForTheQueue() {
        assertSheet(SportLessonSheetContent(sheet: samples.lesson(offer: "queue")), named: "lesson-queue", appearances: [.light, .dark])
    }

    func testLessonBooked() {
        assertSheet(SportLessonSheetContent(sheet: samples.lesson(offer: "booked")), named: "lesson-booked", appearances: [.light, .dark])
    }

    func testJoinAFullLesson() {
        assertSheet(SportJoinSheet(join: samples.join(kind: "full")) { _ in }, named: "join-full")
    }

    func testJoinAFutureLesson() {
        assertSheet(SportJoinSheet(join: samples.join(kind: "future")) { _ in }, named: "join-future", appearances: [.light, .dark])
    }

    func testMySectionsWithTheRepeat() {
        assertPushed(
            SportSectionsContent(state: samples.sections(queue: "on")),
            title: "sport_sections_title",
            subtitle: samples.sections(queue: "on").subtitle?.resolved ?? "",
            named: "sections-on"
        )
    }

    func testMySectionsAtTheLimit() {
        assertPushed(
            SportSectionsContent(state: samples.sections(queue: "limit")),
            title: "sport_sections_title",
            subtitle: samples.sections(queue: "limit").subtitle?.resolved ?? "",
            named: "sections-limit",
            appearances: [.light, .dark]
        )
    }

    func testMySectionsBeforeBackend() {
        assertPushed(
            SportSectionsContent(state: samples.sectionsWithoutRepeat),
            title: "sport_sections_title",
            named: "sections-plain",
            appearances: [.light, .dark]
        )
    }

    func testPoints() {
        assertPushed(
            SportPointsContent(state: samples.points(progress: "short")),
            title: "sport_points_title",
            subtitle: AppStrings.string("sport_points_subtitle"),
            named: "points-short"
        )
    }

    func testPointsCredited() {
        assertPushed(
            SportPointsContent(state: samples.points(progress: "done")),
            title: "sport_points_title",
            named: "points-done",
            appearances: [.light, .dark]
        )
    }

    private func assertSport(
        _ tab: SportTab,
        my: SportMyUiState? = nil,
        sign: SportSignUiState? = nil,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let myState = my ?? samples.my(state: "active")
        let signState = sign ?? samples.sign(filters: "none", day: "normal")
        let screen = ShellTabs(selection: .constant(.sport), openPass: {}) { _ in
            NavigationStack {
                SportContent(tab: .constant(tab), my: myState, sign: signState)
                    .itmoRootHeader(title: ShellTab.sport.title, subtitle: nil)
            }
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }

    private func assertSheet<Sheet: View>(
        _ sheet: Sheet,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) { sheet }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1.5, testName: testName)
    }

    private func assertPushed<Content: View>(
        _ content: Content,
        title: String,
        subtitle: String = "",
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            content
                .navigationTitle(Text(verbatim: AppStrings.string(title)))
                .navigationSubtitle(Text(verbatim: subtitle))
                .toolbarTitleDisplayMode(.inlineLarge)
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
