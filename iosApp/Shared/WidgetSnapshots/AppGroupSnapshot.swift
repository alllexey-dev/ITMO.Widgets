import Foundation

/// The App Group files the extensions read (docs/ios.md, Data sharing): `<name>-v<N>.json`, each holding
/// `{"version": N, "value": ...}`. The app writes them through the shared `AppGroupSnapshotWriter`; Swift reads them
/// here, in every process, without Kotlin.
///
/// Reading never throws: a missing file, a corrupt one and one written by a newer version (a `version` above the
/// reader's) all come back as nil, and the reader shows its placeholder (ADR 0027).
enum AppGroupSnapshot {
    /// The App Group container of this process: the group in its own Info.plist (`AppGroupID`), then any group
    /// AltStore lists in `ALTAppGroups` (SP-23), as the Kotlin `AppGroupDirectory` resolves it. Nil in a build
    /// without the entitlement; the extensions then see nothing and show the signed-out state.
    static func container(bundle: Bundle = .main, fileManager: FileManager = .default) -> URL? {
        groupCandidates(of: bundle).lazy
            .compactMap { fileManager.containerURL(forSecurityApplicationGroupIdentifier: $0) }
            .first
    }

    /// `AppGroupID`, then `ALTAppGroups`, without repeats.
    static func groupCandidates(of bundle: Bundle) -> [String] {
        let own = (bundle.object(forInfoDictionaryKey: "AppGroupID") as? String).map { [$0] } ?? []
        let altStore = bundle.object(forInfoDictionaryKey: "ALTAppGroups") as? [String] ?? []
        var seen = Set<String>()
        return (own + altStore).filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    /// `<name>-v<version>.json`.
    static func fileName(_ name: String, version: Int) -> String {
        "\(name)-v\(version).json"
    }

    /// The value in `data`, or nil when it is corrupt or its `version` is above `maxVersion`.
    static func decode<Value: Decodable>(_ type: Value.Type, from data: Data, maxVersion: Int) -> Value? {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let text = try container.decode(String.self)
            guard let date = parseInstant(text) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "Not an ISO 8601 instant")
            }
            return date
        }
        // The version is read on its own first, so a newer file with another shape is rejected, not misread.
        guard let header = try? decoder.decode(Header.self, from: data), header.version <= maxVersion,
              let envelope = try? decoder.decode(Envelope<Value>.self, from: data)
        else { return nil }
        return envelope.value
    }

    /// The value of `file` in `container`, or nil when the container or the file is missing or the file is rejected.
    static func read<Value: Decodable>(_ type: Value.Type, file: String, maxVersion: Int, in container: URL?)
        -> Value? {
        guard let container, let data = try? Data(contentsOf: container.appendingPathComponent(file)) else {
            return nil
        }
        return decode(type, from: data, maxVersion: maxVersion)
    }

    /// Replaces `file` in `container` with `value` at `version`. The write goes to a temporary file that replaces
    /// the old one, so a reader sees the old or the new file, never a partial one; no lock is taken (0xdead10cc).
    static func write<Value: Encodable>(_ value: Value, file: String, version: Int, in container: URL) throws {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(formatInstant(date))
        }
        let data = try encoder.encode(Envelope(version: version, value: value))
        try data.write(to: container.appendingPathComponent(file), options: .atomic)
    }

    /// Kotlin's `Instant.toString()`: ISO 8601 in UTC, with a fraction of a second only when there is one.
    static func parseInstant(_ text: String) -> Date? {
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: text) ?? ISO8601DateFormatter().date(from: text)
    }

    /// ISO 8601 in UTC with milliseconds, which `parseInstant` and Kotlin's `Instant.parse` both read.
    static func formatInstant(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private struct Header: Decodable {
        let version: Int
    }

    fileprivate struct Envelope<Value> {
        let version: Int
        let value: Value
    }
}

extension AppGroupSnapshot.Envelope: Decodable where Value: Decodable {}
extension AppGroupSnapshot.Envelope: Encodable where Value: Encodable {}
