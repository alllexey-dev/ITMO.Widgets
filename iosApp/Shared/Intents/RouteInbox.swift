import Foundation

/// Hands the route ids of App Intents and quick actions (`IntentRoute`) to the app's router, which offers them to the
/// shared `RouteQueue` (`AppRouter.open(id:)`). The intents compile into the widget extension too, which links no
/// Kotlin and has no router, so they reach the router only through this box. The app connects its router in
/// `App.init`; an id that arrives before that waits here, and a newer one replaces it, as in the queue.
@MainActor
final class RouteInbox {
    static let shared = RouteInbox()

    private var receiver: ((String) -> Bool)?
    private(set) var waiting: String?

    /// Connects the receiver of every later id and hands it the waiting one.
    func connect(_ receiver: @escaping (String) -> Bool) {
        self.receiver = receiver
        if let id = waiting {
            waiting = nil
            _ = receiver(id)
        }
    }

    /// Offers a route id: to the connected receiver, which says whether it routes the id, or to the box until one
    /// connects.
    @discardableResult
    func offer(id: String) -> Bool {
        guard let receiver else {
            waiting = id
            return true
        }
        return receiver(id)
    }
}
