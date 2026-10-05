import UserNotifications

/// The notification service extension (Swift micro-client, no Kotlin; ADR 0023). IO-12a adds the payload handling;
/// until then every notification is delivered unchanged.
final class NotificationService: UNNotificationServiceExtension {
    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        contentHandler(request.content)
    }
}
