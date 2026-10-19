import SwiftUI

/// The body of a report form sheet (a review, a link): the reasons as an inline picker, an optional comment of up to
/// `maxComment` code points with its counter, and the last failure under it. Put it in a `NavigationStack` with the
/// title, cancel and send in the toolbar.
struct ItmoReportForm<Reason: Hashable>: View {
    let reasons: [Reason]
    let label: (Reason) -> String
    let commentLabel: String
    var maxComment = 500
    @Binding var reason: Reason?
    @Binding var comment: String
    var failure: String?
    var sending = false

    var body: some View {
        Form {
            Section {
                Picker(selection: $reason) {
                    ForEach(reasons, id: \.self) { reason in
                        Text(verbatim: label(reason)).tag(Optional(reason))
                    }
                } label: {
                    EmptyView()
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section {
                TextField(
                    text: Binding(get: { comment }, set: { comment = limited($0) }),
                    prompt: Text(verbatim: commentLabel),
                    axis: .vertical
                ) { Text(verbatim: commentLabel) }
                .lineLimit(3...8)
                .accessibilityIdentifier("report_comment")
            } footer: {
                VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                    Text(verbatim: "\(comment.unicodeScalars.count)/\(maxComment)").monospacedDigit()
                    if let failure {
                        Text(verbatim: failure).foregroundStyle(ItmoColor.error)
                    }
                }
            }
        }
        .itmoTint()
        .disabled(sending)
    }

    /// Backend counts code points, as `TeacherReviewLimits.length` does.
    private func limited(_ text: String) -> String {
        guard text.unicodeScalars.count > maxComment else { return text }
        return String(String.UnicodeScalarView(text.unicodeScalars.prefix(maxComment)))
    }
}
