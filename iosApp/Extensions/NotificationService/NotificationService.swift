import UserNotifications

/// The notification service extension (Swift micro-client, no Kotlin; ADR 0023, SP-16a). `PushHandler` decides what
/// each push shows; when iOS ends the extension's time first, Backend's own alert is delivered, and Backend's next
/// attempt retries the booking.
final class NotificationService: UNNotificationServiceExtension {
    /// Replaced by tests with a handler on fixtures.
    var makeHandler: () -> PushHandler = { PushHandler.live() }

    private var delivery: Delivery?
    private var work: Task<Void, Never>?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        let delivery = Delivery(fallback: request.content, handler: contentHandler)
        let handler = makeHandler()
        self.delivery = delivery
        work = Task { delivery.deliver(await handler.handle(request.content)) }
    }

    override func serviceExtensionTimeWillExpire() {
        work?.cancel()
        delivery?.deliverFallback()
    }
}

/// Hands one content to the system, whichever of the handler and the time limit comes first.
private final class Delivery {
    private let fallback: UNNotificationContent
    private var handler: ((UNNotificationContent) -> Void)?
    private let lock = NSLock()

    init(fallback: UNNotificationContent, handler: @escaping (UNNotificationContent) -> Void) {
        self.fallback = fallback
        self.handler = handler
    }

    func deliver(_ content: UNNotificationContent) {
        lock.lock()
        let handler = handler
        self.handler = nil
        lock.unlock()
        handler?(content)
    }

    func deliverFallback() {
        deliver(fallback)
    }
}
