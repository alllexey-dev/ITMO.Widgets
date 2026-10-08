import EventKit
import SwiftUI

/// The calendar access the sync needs (IO-15b): full access to events, since write-only access can neither create the
/// app's calendar nor read its events back. The Kotlin side (`SystemEventStore`) only reads the status; asking is the
/// app's, here, with `NSCalendarsFullAccessUsageDescription` as the system prompt's text.
struct CalendarAccess {
    /// What turning the sync on leads to.
    enum Outcome: Equatable {
        /// Full access: the sync turns on (`SettingsViewModel.onCalendarAccessGranted`).
        case granted
        /// The user refused the system prompt just now: a short message, as Android's first refusal.
        case refused
        /// Refused before, or restricted: only the app's page in Settings can change it, so the rationale offers it.
        case rationale
    }

    var status: () -> EKAuthorizationStatus = { EKEventStore.authorizationStatus(for: .event) }
    var requestFullAccess: () async -> Bool = {
        (try? await EKEventStore().requestFullAccessToEvents()) ?? false
    }

    /// Never asked (or only write-only access, which the prompt upgrades): the system prompt once. Asked and refused
    /// before: no prompt, iOS shows none again.
    func turnOn() async -> Outcome {
        switch status() {
        case .fullAccess:
            return .granted
        case .notDetermined, .writeOnly:
            return await requestFullAccess() ? .granted : .refused
        default:
            return .rationale
        }
    }
}

/// Why the app asks for the calendar, after a refusal for good: `calendar_access_open_settings` opens the app's page
/// in Settings, `calendar_access_later` leaves the switch off. Android's `SettingsDialog.CalendarAccess(locked = true)`.
struct CalendarAccessRationale: ViewModifier {
    @Binding var isPresented: Bool
    let openSettings: () -> Void

    func body(content: Content) -> some View {
        content.alert(
            Text(verbatim: AppStrings.string("calendar_access_title")),
            isPresented: $isPresented
        ) {
            Button(action: openSettings) {
                Text(verbatim: AppStrings.string("calendar_access_open_settings"))
            }
            Button(role: .cancel) {} label: {
                Text(verbatim: AppStrings.string("calendar_access_later"))
            }
        } message: {
            Text(verbatim: AppStrings.string("calendar_access_rationale"))
        }
    }
}

extension View {
    func calendarAccessRationale(isPresented: Binding<Bool>, openSettings: @escaping () -> Void) -> some View {
        modifier(CalendarAccessRationale(isPresented: isPresented, openSettings: openSettings))
    }
}
