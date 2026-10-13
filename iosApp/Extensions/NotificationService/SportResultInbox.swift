import Foundation

/// The queue results the notification service leaves for the bell (docs/ios.md, Data sharing): one
/// `sport-results/<lesson>-<millis>.json` per booked or refused lesson in the App Group, a `SportResult` of the shared
/// sport feature (`lessonId`, `sectionName`, `start` and `at` in epoch milliseconds, `booked`), written under a
/// temporary name and moved in place, so the app (`AppGroupSportResultInbox`) only ever lists whole files and deletes
/// each one it read. Best effort: a failed write costs the bell's row, never the notification.
struct SportResultInbox {
    let directory: URL?

    init(container: URL?) {
        directory = container?.appendingPathComponent(Self.name, isDirectory: true)
    }

    func record(_ notice: SportSignNotice, at moment: Date) {
        guard let directory else { return }
        let at = Self.millis(moment)
        let result: [String: Any] = [
            "lessonId": notice.lessonId,
            "sectionName": notice.sectionName,
            "start": Self.millis(notice.start),
            "booked": notice.booked,
            "at": at,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: result) else { return }
        let file = directory.appendingPathComponent("\(notice.lessonId)-\(at).json")
        let temporary = directory.appendingPathComponent("\(notice.lessonId)-\(at).json.tmp")
        let files = FileManager.default
        do {
            try files.createDirectory(at: directory, withIntermediateDirectories: true)
            try data.write(to: temporary)
            if files.fileExists(atPath: file.path) { try files.removeItem(at: file) }
            try files.moveItem(at: temporary, to: file)
        } catch {
            try? files.removeItem(at: temporary)
        }
    }

    static let name = "sport-results"

    private static func millis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }
}
