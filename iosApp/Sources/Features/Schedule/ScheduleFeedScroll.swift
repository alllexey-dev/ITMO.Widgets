import Foundation

enum ScheduleFeedScroll {
    /// Lazy rows need a second positioning pass after layout; an interrupted jump must never perform that pass.
    @MainActor
    static func settle(scroll: () -> Void, completed: () -> Void) async {
        guard !Task.isCancelled else { return }
        scroll()
        do {
            try await Task.sleep(for: .milliseconds(100))
        } catch {
            return
        }
        guard !Task.isCancelled else { return }
        scroll()
        completed()
    }
}
