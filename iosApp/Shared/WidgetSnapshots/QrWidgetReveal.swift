import Foundation

/// The QR widget's own state: until when a tap has revealed the code, `qr-widget-v1.json` in the App Group container.
/// Only `RevealQrIntent` writes it, in the widget extension; the app never reads it, and sign-out removes it with the
/// rest of the container. One file for every placed QR widget: a `StaticConfiguration` widget has no instance id, so
/// a tap reveals all of them, as Android's global options would.
struct QrWidgetReveal: Equatable, Codable {
    /// Past this instant the spoiler covers the code again.
    let revealedUntil: Date

    /// How long a tap reveals the code, Android's auto-hide delay (`QrWidgetWork.AUTO_HIDE_DELAY_MILLIS`).
    static let duration: TimeInterval = 30

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("qr-widget", version: version)

    /// The snapshot version this build writes and the highest it reads.
    static let version = 1

    /// The reveal a tap at `now` starts.
    static func startingAt(_ now: Date) -> QrWidgetReveal {
        QrWidgetReveal(revealedUntil: now.addingTimeInterval(duration))
    }

    /// The reveal in the App Group `container`; nil when there is none or it is unreadable (the spoiler shows).
    static func read(fromContainer container: URL?) -> QrWidgetReveal? {
        AppGroupSnapshot.read(QrWidgetReveal.self, file: fileName, maxVersion: version, in: container)
    }

    /// Replaces the reveal in `container`.
    func write(toContainer container: URL) throws {
        try AppGroupSnapshot.write(self, file: Self.fileName, version: Self.version, in: container)
    }
}
