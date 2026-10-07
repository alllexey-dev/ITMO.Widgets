import Shared

/// Runs the shared `PushForegroundRefresh` (IO-13a) on every return to the foreground: the notification settings
/// for the extensions' session snapshot, then the push registration, which reaches Backend only when the token,
/// the account or the alerts answer changed. Nothing is sent without a push token (IO-13b), in the demo or without
/// the opt-in.
enum PushRefresh {
    @MainActor
    static func run() async {
        guard IosKoin.shared.isStarted,
              let refresh = IosKoin.shared.get(type: PushForegroundRefresh.self) as? PushForegroundRefresh else {
            return
        }
        try? await refresh.refresh()
    }
}
