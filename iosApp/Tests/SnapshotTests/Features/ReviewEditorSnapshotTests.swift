import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The review editor's form sheet in the states of the Compose previews: a new review with three suggestions, the own
/// review opened again with the name shown, and a text below the minimum after a send.
@MainActor
final class ReviewEditorSnapshotTests: XCTestCase {
    private static let teacher = "Константинопольская Александра Константиновна"

    func testNewReview() {
        assertEditor(
            form(suggestions: ["Математический анализ", "Линейная алгебра", "Дискретная математика"]),
            named: "new"
        )
    }

    func testOwnReview() {
        assertEditor(
            form(
                subject: "Математический анализ",
                text: "Лекции понятные, на практике разбираем задачи из контрольных.",
                anonymous: false,
                editing: true
            ),
            named: "edit",
            appearances: [.light, .dark]
        )
    }

    func testTooShort() {
        let text = String(repeating: "а", count: Int(TeacherReviewLimits.shared.MIN_TEXT) - 1)
        assertEditor(form(text: text, textError: .textTooShort), named: "too-short", appearances: [.light, .dark])
    }

    private func form(
        subject: String = "",
        text: String = "",
        anonymous: Bool = true,
        suggestions: [String] = [],
        textError: ReviewFieldError? = nil,
        editing: Bool = false
    ) -> ReviewEditorUiState {
        ReviewEditorUiState(
            subject: subject, text: text, anonymous: anonymous, suggestions: suggestions, subjectError: nil,
            textError: textError, saving: false, editing: editing
        )
    }

    private func assertEditor(
        _ form: ReviewEditorUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let sheet = NavigationStack {
            ReviewEditorForm(
                form: form, subject: .constant(form.subject), text: .constant(form.text), anonymous: .constant(form.anonymous)
            )
            .navigationTitle(Text(verbatim: AppStrings.string(form.editing ? "review_editor_edit" : "review_editor_new")))
            .navigationSubtitle(Text(verbatim: UserNamesKt.shortPersonName(name: Self.teacher)))
            .navigationBarTitleDisplayMode(.inline)
        }
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) { sheet }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 2, testName: testName)
    }
}
