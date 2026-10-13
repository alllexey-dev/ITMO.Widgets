import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The recordbook in the shell (the root header with the period menu, the glass tab bar) on the shared
/// `RecordbookUiState` the real presenter builds from the synthetic third semester of `RecordbookSamples`, at the design
/// lab's axes (moment, BARS), its states and the sources sheet. Drawn in a window, 375 pt wide.
@MainActor
final class RecordbookSnapshotTests: XCTestCase {
    private let samples = RecordbookSamples.shared

    func testSemester() {
        assertRecordbook(samples.list(moment: "mid", bars: "on", sources: nil), named: "semester")
    }

    func testWithoutBars() {
        assertRecordbook(samples.list(moment: "mid", bars: "off", sources: nil), named: "bars-off", scrolled: true)
    }

    func testBarsExpired() {
        assertRecordbook(samples.list(moment: "mid", bars: "expired", sources: nil), named: "bars-expired", appearances: [.light, .dark])
    }

    func testSession() {
        assertRecordbook(samples.list(moment: "session", bars: "on", sources: nil), named: "session")
    }

    func testEmpty() {
        assertRecordbook(samples.Empty, named: "empty", appearances: [.light, .dark])
    }

    func testFailed() {
        assertRecordbook(samples.Failed, named: "failed", appearances: [.light, .dark])
    }

    func testSourcesMismatch() {
        assertSources("mismatch")
    }

    func testSourcesBars() {
        assertSources("bars", appearances: [.light, .dark])
    }

    private func assertSources(
        _ kind: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let sources = samples.sources(case: kind)
        let screen = Color(.systemGroupedBackground)
            .sheet(isPresented: .constant(true)) { RecordbookSourcesSheet(sources: sources) {} }
        assertScreenAppearances(of: screen, named: "sources-\(kind)", appearances: appearances, settle: 1.5, testName: testName)
    }

    private func assertRecordbook(
        _ state: RecordbookUiState,
        named name: String,
        scrolled: Bool = false,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = ShellTabs(selection: .constant(.recordbook), openPass: {}) { _ in
            NavigationStack {
                RecordbookContent(state: state)
                    .defaultScrollAnchor(scrolled ? .bottom : .top)
                    .itmoRootHeader(title: ShellTab.recordbook.title, subtitle: state.subtitle?.resolved)
                    .toolbar {
                        if let periods = state.periods {
                            ToolbarItem(placement: .topBarTrailing) { RecordbookPeriodMenu(periods: periods) { _, _ in } }
                        }
                    }
            }
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}

/// The subject page under the native bar on the shared `RecordbookSubjectUiState` of `RecordbookSamples`: each tab at
/// the design lab's axes, and the error.
@MainActor
final class RecordbookSubjectSnapshotTests: XCTestCase {
    private let samples = RecordbookSamples.shared

    func testScoresFromBars() {
        assertSubject(subject(.scores, scores: "bars"), named: "scores-bars", appearances: SnapshotAppearance.all)
    }

    func testScoresWithoutDetails() {
        assertSubject(subject(.scores, scores: "plain"), named: "scores-plain")
    }

    func testScoresFromASheet() {
        assertSubject(subject(.scores, scores: "sheet"), named: "scores-sheet")
    }

    func testLessonsWithTones() {
        assertSubject(subject(.lessons, reviews: "summary"), named: "lessons-summary", appearances: SnapshotAppearance.all)
    }

    func testLessonsWithFewReviews() {
        assertSubject(subject(.lessons, reviews: "few"), named: "lessons-few")
    }

    func testLessonsNotConnected() {
        assertSubject(subject(.lessons, reviews: "off"), named: "lessons-off")
    }

    func testMaterials() {
        assertSubject(subject(.materials, materials: "links"), named: "materials-links", appearances: SnapshotAppearance.all)
    }

    func testMaterialsEmpty() {
        assertSubject(subject(.materials, materials: "empty"), named: "materials-empty")
    }

    func testMaterialsNotConnected() {
        assertSubject(subject(.materials, materials: "off"), named: "materials-off")
    }

    func testFailed() {
        assertSubject(samples.SubjectFailed, named: "failed")
    }

    private func subject(_ tab: SubjectTab, scores: String = "bars", reviews: String = "summary", materials: String = "links")
        -> RecordbookSubjectUiState {
        samples.subject(tab: tab, scores: scores, reviews: reviews, materials: materials)
    }

    private func assertSubject(
        _ state: RecordbookSubjectUiState,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        testName: String = #function
    ) {
        let content = state as? RecordbookSubjectUiStateContent
        let screen = NavigationStack {
            RecordbookSubjectContent(state: state)
                .navigationTitle(Text(verbatim: content?.title ?? ""))
                .navigationSubtitle(Text(verbatim: content?.subtitle.resolved ?? ""))
                .toolbarTitleDisplayMode(.inlineLarge)
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
