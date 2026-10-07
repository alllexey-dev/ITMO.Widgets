import Foundation

/// What the extensions know of the session: `session-v1.json` in the App Group container, written by the shared
/// `SessionSnapshotWriter` for every signed-in session, demo included (docs/ios.md, Data sharing). A missing file
/// means signed out. No token: the notification service reads those from the Keychain.
struct SessionFile: Equatable, Decodable {
    /// The signed-in student's ISU number, when the ID token carries one.
    let isu: Int?
    /// The demo session: the extensions show fictional data and send nothing.
    let demo: Bool
    /// Whether the user allows alert notifications.
    let alertsAllowed: Bool

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("session", version: version)

    /// The highest snapshot version this build reads.
    static let version = 1

    /// The session in the App Group `container`; nil when signed out, unreadable or newer than this build.
    static func read(fromContainer container: URL?) -> SessionFile? {
        AppGroupSnapshot.read(SessionFile.self, file: fileName, maxVersion: version, in: container)
    }
}
