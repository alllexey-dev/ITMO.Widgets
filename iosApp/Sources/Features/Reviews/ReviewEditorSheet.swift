import Shared
import SwiftUI

/// The review editor (`AppRoutes.ReviewEditor`, from `teacher_review_write` or an own review on a teacher's profile)
/// over the shared `ReviewEditorViewModel`, a form sheet at full height as on Android: a drag down does not close it,
/// and the close button asks `review_discard_title` before unsaved changes are dropped. The optional subject with the
/// subjects of the viewer's lessons with the teacher as chips, the text with its counter and the minimum as a hint
/// that turns into the error on send, `review_anonymous` with its warning when off. A saved review closes the sheet; a failed
/// save says why over it.
struct ReviewEditorSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<ReviewEditorViewModel, ReviewEditorUiState>
    @State private var messages = ScheduleMessages()
    @State private var discarding = false

    init(args: TeacherReviewArgs) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = reviewEditorParameters(args: args)
            guard let model = store.resolve(type: ReviewEditorViewModel.self, parameters: parameters)
                as? ReviewEditorViewModel else {
                preconditionFailure("Koin resolved no ReviewEditorViewModel")
            }
            return model
        })
    }

    var body: some View {
        let form = model.state
        let viewModel = model.viewModel
        NavigationStack {
            ReviewEditorForm(
                form: form,
                // The fields read the ViewModel's current value, which an edit changes at once, not the observed one.
                subject: Binding(get: { viewModel.uiState.value.subject }, set: { viewModel.onSubjectChanged(subject: $0) }),
                text: Binding(get: { viewModel.uiState.value.text }, set: { viewModel.onTextChanged(text: $0) }),
                anonymous: Binding(get: { form.anonymous }, set: { viewModel.onAnonymousChanged(anonymous: $0) })
            )
            .navigationTitle(Text(verbatim: AppStrings.string(form.editing ? "review_editor_edit" : "review_editor_new")))
            .navigationSubtitle(Text(verbatim: UserNamesKt.shortPersonName(name: viewModel.teacherName)))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .close) {
                        if viewModel.hasChanges() { discarding = true } else { dismiss() }
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("review_editor_close")))
                    .accessibilityIdentifier("review_editor_close")
                }
                ToolbarItem(placement: .confirmationAction) {
                    let send = AppStrings.string(form.editing ? "review_save" : "review_send")
                    Button { viewModel.save() } label: {
                        if form.saving { ProgressView() } else { Text(verbatim: send) }
                    }
                    .disabled(form.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || form.saving)
                    .accessibilityLabel(Text(verbatim: send))
                    .accessibilityIdentifier("review_editor_send")
                }
            }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled()
        .alert(Text(verbatim: AppStrings.string("review_discard_title")), isPresented: $discarding) {
            Button(role: .destructive) { dismiss() } label: { Text(verbatim: AppStrings.string("review_discard")) }
            Button(role: .cancel) {} label: { Text(verbatim: AppStrings.string("common_cancel")) }
        }
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case .saved: dismiss()
            case let .failed(failed): messages.show(AppErrorTextsKt.toUiText(failed.error).resolved)
            }
        }
        .scheduleMessages(messages)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("reviews.editor")
    }
}

/// The review form without the ViewModel.
struct ReviewEditorForm: View {
    let form: ReviewEditorUiState
    @Binding var subject: String
    @Binding var text: String
    @Binding var anonymous: Bool

    var body: some View {
        Form {
            Section {
                TextField(
                    text: $subject,
                    prompt: Text(verbatim: AppStrings.string("review_subject_hint")),
                    axis: .vertical
                ) { Text(verbatim: AppStrings.string("review_subject_hint")) }
                .lineLimit(1...3)
                .textInputAutocapitalization(.sentences)
                .accessibilityIdentifier("review_editor_subject")
                if !form.suggestions.isEmpty {
                    // A picked chip clears the subject again.
                    ItmoChips(chips: form.suggestions.map { ItmoChip(label: $0, selected: $0 == form.subject) }) { index in
                        let name = form.suggestions[index]
                        subject = name == form.subject ? "" : name
                    }
                    .padding(.vertical, ItmoSpacing.related)
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("review_editor_suggestions")
                }
            } footer: {
                if let error = form.subjectError { Text(verbatim: Self.text(of: error)).foregroundStyle(ItmoColor.error) }
            }
            Section {
                TextField(text: $text, prompt: Text(verbatim: AppStrings.string("review_text_hint")), axis: .vertical) {
                    Text(verbatim: AppStrings.string("review_text_hint"))
                }
                .lineLimit(5...)
                .textInputAutocapitalization(.sentences)
                .accessibilityIdentifier("review_editor_text")
            } footer: {
                HStack(alignment: .firstTextBaseline) {
                    if let error = form.textError {
                        Text(verbatim: Self.text(of: error)).foregroundStyle(ItmoColor.error)
                    } else if form.showsMinimumHint {
                        Text(verbatim: Self.text(of: .textTooShort))
                    }
                    Spacer(minLength: ItmoSpacing.content)
                    Text(verbatim: "\(TeacherReviewLimits.shared.length(text: form.text))/\(TeacherReviewLimits.shared.MAX_TEXT)")
                        .monospacedDigit()
                }
            }
            Section {
                Toggle(isOn: $anonymous) { Text(verbatim: AppStrings.string("review_anonymous")) }
                    .accessibilityIdentifier("review_editor_anonymous")
            } footer: {
                if !form.anonymous { Text(verbatim: AppStrings.string("review_anonymous_off")) }
            }
        }
        .itmoTint()
        .disabled(form.saving)
    }

    private static func text(of error: ReviewFieldError) -> String {
        switch error {
        case .textTooShort: AppStrings.string("review_text_too_short", [Int(TeacherReviewLimits.shared.MIN_TEXT)])
        case .textTooLong: AppStrings.string("review_text_too_long", [Int(TeacherReviewLimits.shared.MAX_TEXT)])
        case .subjectTooLong: AppStrings.string("review_subject_too_long", [Int(TeacherReviewLimits.shared.MAX_SUBJECT)])
        }
    }
}
