import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The sport tab in the shell (the static root header, the glass tab bar) on the shared states the real presenters
/// build from Мария's synthetic Monday of `SportSamples`, at the design lab's axes; the lesson, filters and queue
/// sheets (filters with "more" and with every type); `Откуда баллы` under the native bar. Drawn in a window, 375 pt wide.
@MainActor
final class SportSnapshotTests: XCTestCase {
    private let samples = SportSamples.shared

    func testMine() {
        assertSport(.my, my: samples.my(state: "active"), named: "my")
    }

    func testMineEmpty() {
        assertSport(.my, my: samples.my(state: "none"), named: "my-none", appearances: [.light, .dark])
    }

    func testMineSections() {
        let sections = samples.my(state: "active").sections
        let screen = ScrollView {
            VStack(alignment: .leading, spacing: ItmoSpacing.content) {
                ForEach(sections, id: \.id) { SportSectionGroup(section: $0, actions: SportContentActions()) }
                ItmoGroupNote(text: AppStrings.string("sport_section_note"))
            }
            .padding(.horizontal, ItmoSpacing.screenMargin)
            .padding(.top, 60)
        }
        .background(Color(.systemGroupedBackground))
        assertScreenAppearances(of: screen, named: "my-sections", appearances: [.light, .dark], settle: 1)
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

    func testSignUpNextWeek() {
        assertSport(.sign, sign: samples.sign(filters: "none", day: "week2"), named: "sign-week2", appearances: [.light, .dark])
    }

    func testFilters() {
        assertSheet(SportFiltersSheet(filters: samples.sign(filters: "sheet", day: "normal").filters!), named: "filters")
    }

    func testFiltersWithEveryType() {
        assertSheet(
            SportFiltersSheet(filters: samples.sign(filters: "sheet-all", day: "normal").filters!),
            named: "filters-all",
            appearances: [.light, .dark]
        )
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
        assertSheet(
            SportLessonSheetContent(sheet: samples.lesson(offer: "booked"), section: samples.sectionToggle(state: "on")),
            named: "lesson-booked",
            appearances: [.light, .dark]
        )
    }

    func testLessonBookedInAFullSection() {
        assertSheet(
            SportLessonSheetContent(sheet: samples.lesson(offer: "booked"), section: samples.sectionToggle(state: "full")),
            named: "lesson-section-full",
            appearances: [.light, .dark]
        )
    }

    func testJoinAFullLesson() {
        assertSheet(SportJoinSheet(join: samples.join(kind: "full")) { _ in }, named: "join-full")
    }

    func testJoinAFutureLesson() {
        assertSheet(SportJoinSheet(join: samples.join(kind: "future")) { _ in }, named: "join-future", appearances: [.light, .dark])
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
                .itmoDetailHeader(title: AppStrings.string(title), subtitle: subtitle)
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
