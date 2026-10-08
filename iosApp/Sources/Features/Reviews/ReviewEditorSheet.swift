import Shared
import SwiftUI

/// The review editor (`AppRoutes.ReviewEditor`, from `teacher_review_write` or an own review on a teacher's profile):
/// the Compose sheet content (`reviewEditorViewController`, IO-09f) at full height, a form sheet as on Android: a drag
/// down does not close it, the content's close button asks before unsaved changes are dropped. A saved review closes
/// it; a failed save says why over it.
struct ReviewEditorSheetView: View {
    let args: TeacherReviewArgs
    @Environment(\.dismiss) private var dismiss
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [args, messages, dismiss] in
            reviewEditorViewController(args: args, say: { text in messages.show(text) }, onClose: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("reviews.editor")
        .scheduleMessages(messages)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .presentationBackground(Color(uiColor: .systemGroupedBackground))
        .interactiveDismissDisabled()
    }
}
