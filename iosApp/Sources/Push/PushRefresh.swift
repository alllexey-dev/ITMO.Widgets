import Shared

/// Runs the shared `PushForegroundRefresh` on every return to the foreground and on every new FCM token
/// (`PushRegistration`): the notification settings for the extensions' session snapshot, then the push registration,
/// which reaches Backend only when the token, the account or the alerts answer changed. Nothing is sent without a
/// push token, in the demo or without the opt-in.
enum PushRefresh {
    @MainActor
    static func run() async {
        guard IosKoin.shared.isStarted,
              let refresh = IosKoin.shared.get(type: PushForegroundRefresh.self) as? PushForegroundRefresh else {
            return
        }
        if let token = PushRegistration.shared.fcmToken {
            updateToken(token)
        }
        try? await refresh.refresh()
    }

    /// Hands the FCM token to the shared `IosPushDevice`; nil or blank clears it.
    @MainActor
    static func updateToken(_ token: String?) {
        guard IosKoin.shared.isStarted,
              let device = IosKoin.shared.get(type: IosPushDevice.self) as? IosPushDevice else {
            return
        }
        device.updateToken(token: token)
    }
}
