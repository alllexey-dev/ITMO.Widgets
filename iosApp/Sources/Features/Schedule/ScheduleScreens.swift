import Shared
import SwiftUI

/// Another user's schedule (`AppRoutes.UserSchedule`, from a profile): L10's Compose route
/// (`userScheduleViewController`, IO-09b); every lesson opens the lesson sheet.
struct UserScheduleScreen: View {
    let isu: Int32
    let name: String
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [isu, name, dismiss, router, messages] in
            userScheduleViewController(
                isu: isu,
                name: name,
                open: { route in messages.opened(router.open(route)) },
                onBack: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("schedule.user")
        .scheduleMessages(messages)
    }
}

/// The found schedule changes (`AppRoutes.ScheduleChanges`, from the home card or a change notification): L10's
/// Compose route (`scheduleChangesViewController`, IO-09b), which marks the shown changes read.
struct ScheduleChangesScreen: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ComposeHost { [dismiss] in
            scheduleChangesViewController(onBack: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("schedule.changes")
    }
}

/// One lesson occurrence (`AppRoutes.LessonDetails`, from the schedule, home or a widget): the Compose sheet content
/// (`lessonDetailsViewController`, IO-09b) in a full-height sheet, as Android's 90 % sheet. The place opens in Apple
/// Maps, the meeting link in Safari; a profile opens in the sheet's place.
struct LessonDetailsSheet: View {
    let lesson: LessonDetailsArgs
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [lesson, dismiss, router, messages] in
            lessonDetailsViewController(
                lesson: lesson,
                open: { route in messages.opened(router.open(route)) },
                onClose: { dismiss() },
                mapUnavailable: { messages.say("schedule_map_unavailable") },
                linkFailed: { messages.say("link_open_failed") }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("schedule.lessonDetails")
        .scheduleMessages(messages)
        .scheduleSheet()
    }
}

/// A queued or predicted sport booking of the schedule (`AppRoutes.PendingSportDetails`): the Compose sheet content
/// (`pendingSportDetailsViewController`, IO-09b); its sport button closes it and selects the sport tab.
struct PendingSportDetailsSheet: View {
    let booking: PendingSportDetailsArgs
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [booking, dismiss, router, messages] in
            pendingSportDetailsViewController(
                booking: booking,
                open: { route in messages.opened(router.open(route)) },
                onClose: { dismiss() },
                mapUnavailable: { messages.say("schedule_map_unavailable") }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("schedule.pendingSport")
        .scheduleMessages(messages)
        .scheduleSheet()
    }
}

/// The schedule's friend picker (`AppRoutes.FriendSelector`): L13's Compose sheet content
/// (`friendSelectorViewController`, IO-09b). The choice goes back to the schedule root through the router before the
/// sheet closes; a profile opens in the sheet's place.
struct FriendSelectorSheet: View {
    let picker: AppRoutes.FriendSelector
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router

    var body: some View {
        ComposeHost { [picker, dismiss, router] in
            friendSelectorViewController(
                selectedIsu: picker.selectedIsu,
                deliver: { user in
                    router.deliver(FriendPick(user: user), from: picker)
                    dismiss()
                },
                open: { route in router.open(route) },
                onClose: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("schedule.friendPicker")
        .scheduleSheet()
    }
}

extension View {
    /// A schedule sheet: full height (Android's `SheetPolicy.Height.TALL`), with the system's drag indicator over the
    /// Compose title row; a drag down closes it. The see-through Compose content shows the grouped background its
    /// `SheetScaffold` draws in the iOS style around itself (the home indicator's band).
    func scheduleSheet() -> some View {
        presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(uiColor: .systemGroupedBackground))
    }
}
