import Shared
import SwiftUI

/// The report of another viewer's review (`AppRoutes.ReportReview`, from a review's more button on a teacher's card)
/// over the shared `ReportReviewViewModel`, a native form sheet: the four reasons, an optional comment of up to 500
/// characters, send once a reason is picked. While it sends the sheet stays; a failure shows under the comment
/// until the next send; an accepted report closes it.
struct ReportReviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<ReportReviewViewModel, ReportReviewUiState>
    @State private var reason: ReviewReportReason?
    @State private var comment = ""
    @State private var failure: String?

    init(args: TeacherReviewArgs, reviewId: String) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = reportReviewParameters(args: args, reviewId: reviewId)
            guard let model = store.resolve(type: ReportReviewViewModel.self, parameters: parameters)
                as? ReportReviewViewModel else {
                preconditionFailure("Koin resolved no ReportReviewViewModel")
            }
            return model
        })
    }

    var body: some View {
        let sending = model.state.sending
        NavigationStack {
            ReportReviewForm(reason: $reason, comment: $comment, failure: failure, sending: sending)
                .navigationTitle(Text(verbatim: AppStrings.string("review_report_title")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(role: .cancel) { dismiss() } label: { Text(verbatim: AppStrings.string("common_cancel")) }
                            .disabled(sending)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            guard let reason else { return }
                            failure = nil
                            model.viewModel.send(reason: reason, comment: comment)
                        } label: {
                            if sending { ProgressView() } else { Text(verbatim: AppStrings.string("review_report_send")) }
                        }
                        .disabled(reason == nil || sending)
                        .accessibilityLabel(Text(verbatim: AppStrings.string("review_report_send")))
                        .accessibilityIdentifier("review_report_send")
                    }
                }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled(sending)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case .done: dismiss()
            case let .failed(failed): failure = AppErrorTextsKt.toUiText(failed.error).resolved
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("reviews.report")
    }
}

/// The reasons and the comment without the ViewModel.
struct ReportReviewForm: View {
    @Binding var reason: ReviewReportReason?
    @Binding var comment: String
    var failure: String?
    var sending = false

    var body: some View {
        Form {
            Section {
                Picker(selection: $reason) {
                    ForEach(ReviewReportReason.allCases, id: \.self) { reason in
                        Text(verbatim: AppStrings.string("review_report_\(reason.name.lowercased())"))
                            .tag(Optional(reason))
                    }
                } label: {
                    EmptyView()
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section {
                TextField(
                    text: Binding(get: { comment }, set: { comment = Self.limited($0) }),
                    prompt: Text(verbatim: AppStrings.string("review_report_comment")),
                    axis: .vertical
                ) { Text(verbatim: AppStrings.string("review_report_comment")) }
                .lineLimit(3...8)
                .accessibilityIdentifier("review_report_comment")
            } footer: {
                VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                    Text(verbatim: "\(comment.unicodeScalars.count)/\(Self.maxComment)").monospacedDigit()
                    if let failure {
                        Text(verbatim: failure).foregroundStyle(ItmoColor.error)
                    }
                }
            }
        }
        .itmoTint()
        .disabled(sending)
    }

    private static let maxComment = Int(TeacherReviewLimits.shared.MAX_COMMENT)

    /// Backend counts code points, as `TeacherReviewLimits.length` does.
    private static func limited(_ text: String) -> String {
        guard text.unicodeScalars.count > maxComment else { return text }
        return String(String.UnicodeScalarView(text.unicodeScalars.prefix(maxComment)))
    }
}
