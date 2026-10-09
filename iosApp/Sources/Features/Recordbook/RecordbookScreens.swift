import Shared
import SwiftUI

/// One subject (`AppRoutes.RecordbookSubject`, from the recordbook): L12's Compose subject page
/// (`recordbookSubjectPage`, IO-09d2). The scores sheet, a teacher's profile and the links sheets (IO-09f) open
/// through the router, the sheet, LMS and subject links in the system, the BARS sign-in in IO-09d1's sheet.
struct RecordbookSubjectView: View {
    let args: RecordbookSubjectArgs
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var host = RecordbookHost()

    var body: some View {
        ComposeHost { [args, router, host, dismiss] in
            host.makeSubject(args: args, router: router, onBack: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.subject")
        .recordbookBarsLogin(host)
        .scheduleMessages(host.messages)
    }
}

/// The period picker (`AppRoutes.RecordbookPeriod`, from the recordbook's period button): the Compose sheet content
/// (`recordbookPeriodViewController`, IO-09d2). The pick answers the tab root through the router before the sheet
/// closes.
struct RecordbookPeriodSheetView: View {
    let period: AppRoutes.RecordbookPeriod
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router

    var body: some View {
        ComposeHost { [period, router, dismiss] in
            recordbookPeriodViewController(
                period: period,
                deliver: { choice in
                    router.deliver(choice, from: period)
                    dismiss()
                },
                onClose: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.period")
        .recordbookSheet()
    }
}

/// The scores sheet (`AppRoutes.SheetScores`, `sheet_scores_title`, from the subject page's sheet total): the Compose
/// sheet content (`sheetScoresViewController`, IO-09d2). It closes itself once the total is saved; a failed save says
/// why over it.
struct SheetScoresSheetView: View {
    let scores: SheetScoresArgs
    @Environment(\.dismiss) private var dismiss
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [scores, dismiss, messages] in
            sheetScoresViewController(
                scores: scores,
                onClose: { dismiss() },
                saveFailed: { text in messages.show(text) }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.sheetScores")
        .scheduleMessages(messages)
        .recordbookSheet()
    }
}

extension View {
    /// A recordbook sheet: half height first and full height on a drag (Android's default sheet height), with the
    /// system's drag indicator over the Compose title row; a drag down closes it. The see-through Compose content shows
    /// the grouped background its `SheetScaffold` draws in the iOS style around itself.
    func recordbookSheet() -> some View {
        presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(uiColor: .systemGroupedBackground))
    }
}

extension ScheduleMessages {
    /// A message whose text the shared content resolved (a failed save of the scores sheet), read aloud too.
    func show(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}
