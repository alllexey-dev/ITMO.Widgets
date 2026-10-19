import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The subject links sheets on the shared `SubjectLinksSamples` and `LinkEditorSamples` (the Compose previews'
/// samples): all links with votes, restricted votes, own links, empty and failed; a link's actions for an own rejected
/// link and another student's Google Sheet; the editor of a new link, of an own link and without the connection; the
/// report of a link.
@MainActor
final class LinksSnapshotTests: XCTestCase {
    private let links = SubjectLinksSamples.shared
    private let editor = LinkEditorSamples.shared

    func testAllLinks() {
        assertAllLinks(links.content, named: "all")
    }

    func testAllLinksWithoutVoting() {
        assertAllLinks(links.restricted, named: "all-restricted", appearances: [.light, .dark])
    }

    func testOwnLinks() {
        assertAllLinks(links.own, named: "all-own", appearances: [.light, .dark])
    }

    func testNoLinks() {
        assertAllLinks(links.empty, named: "all-empty", appearances: [.light])
    }

    func testLinksFailed() {
        assertAllLinks(links.error, named: "all-error", appearances: [.light])
    }

    func testActionsOfAnOwnRejectedLink() {
        assertActions("own-rejected", named: "actions-own")
    }

    func testActionsOfAnotherStudentsSheet() {
        assertActions("others-sheet", named: "actions-shared", appearances: [.light, .dark])
    }

    func testNewLink() {
        assertSheet(editorForm(editor.new), named: "editor-new")
    }

    func testEditOwnLink() {
        assertSheet(editorForm(editor.edit, title: "links_editor_edit"), named: "editor-edit", appearances: [.light, .dark])
    }

    func testNewLinkWithoutTheConnection() {
        assertSheet(editorForm(editor.offline), named: "editor-offline", appearances: [.light, .dark])
    }

    func testNotAnHttpsAddress() {
        assertSheet(editorForm(editor.urlError), named: "editor-url-error", appearances: [.light])
    }

    func testReport() {
        let form = NavigationStack {
            ReportLinkForm(reason: .constant(.wrongSubject), comment: .constant("Ссылка на курс другого семестра"))
                .navigationTitle(Text(verbatim: AppStrings.string("links_report_title")))
                .navigationBarTitleDisplayMode(.inline)
        }
        assertSheet(form, named: "report", appearances: [.light, .dark])
    }

    private func assertAllLinks(
        _ state: SubjectLinksUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let sheet = ItmoSheet(title: AppStrings.string("links_title"), height: .tall) {
            SubjectLinksContent(subject: links.scope.subjectName, state: state)
        }
        .progressViewStyle(StillSpinnerStyle())
        assertSheet(sheet, named: name, appearances: appearances, testName: testName)
    }

    private func assertActions(
        _ linkId: String,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        guard let model = links.content.linkActions(linkId: linkId) else { return XCTFail("no link \(linkId)") }
        let sheet = ItmoSheet(title: AppStrings.string("links_actions")) { LinkActionsContent(model: model) }
        assertSheet(sheet, named: name, appearances: appearances, testName: testName)
    }

    private func editorForm(_ state: LinkEditorUiState, title: String = "links_editor_new") -> some View {
        NavigationStack {
            LinkEditorForm(state: state, url: .constant(state.url), title: .constant(state.title))
                .navigationTitle(Text(verbatim: AppStrings.string(title)))
                .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func assertSheet<Sheet: View>(
        _ sheet: Sheet,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) { sheet }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 2, testName: testName)
    }
}
