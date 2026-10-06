import Foundation

/// The QR pass the app leaves for the QR widget: `qr-pass-v1.json` in the App Group container, written by the shared
/// `QrPassSnapshotWriter` (`:shared:feature-qr`, iosMain) inside the `{"version": N, "value": ...}` envelope of every
/// App Group snapshot (docs/ios.md, Data sharing). The widget draws `matrix` as it is: the modules of the shared QR
/// generator, so it encodes nothing itself.
struct QrPassSnapshot: Equatable {
    /// When the app wrote the snapshot.
    let generatedAt: Date
    /// The local validity deadline of the pass; past it no code is shown.
    let expiresAt: Date
    /// The demo session's pass, a code no turnstile accepts.
    let demo: Bool
    /// Rows from the top, modules from the left; `true` is dark.
    let matrix: [[Bool]]

    /// The file name in the App Group container.
    static let fileName = "qr-pass-v\(version).json"

    /// The highest snapshot version this build reads.
    static let version = 1

    /// The snapshot in `data`, or nil when it is corrupt, not square, or written by a newer version: the reader then
    /// shows its placeholder (ADR 0027).
    static func decode(_ data: Data) -> QrPassSnapshot? {
        guard let envelope = try? JSONDecoder().decode(Envelope.self, from: data), envelope.version <= version,
              let value = envelope.value,
              let generatedAt = parseInstant(value.generatedAt),
              let expiresAt = parseInstant(value.expiresAt)
        else { return nil }
        let matrix = value.matrix.map { row in row.map { $0 == "1" } }
        guard !matrix.isEmpty, matrix.allSatisfy({ $0.count == matrix.count }),
              value.matrix.allSatisfy({ row in row.allSatisfy { $0 == "0" || $0 == "1" } })
        else { return nil }
        return QrPassSnapshot(generatedAt: generatedAt, expiresAt: expiresAt, demo: value.demo, matrix: matrix)
    }

    /// The snapshot in the App Group `container`, or nil when it is missing or unreadable (signed out).
    static func read(fromContainer container: URL) -> QrPassSnapshot? {
        guard let data = try? Data(contentsOf: container.appendingPathComponent(fileName)) else { return nil }
        return decode(data)
    }

    /// Kotlin's `Instant.toString()`: ISO 8601 in UTC, with a fraction of a second only when there is one.
    private static func parseInstant(_ text: String) -> Date? {
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: text) ?? ISO8601DateFormatter().date(from: text)
    }

    private struct Envelope: Decodable {
        let version: Int
        /// Optional, so a newer version with another shape still yields its `version` and is rejected, not misread.
        let value: Value?

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            version = try container.decode(Int.self, forKey: .version)
            value = try? container.decode(Value.self, forKey: .value)
        }

        private enum CodingKeys: String, CodingKey {
            case version
            case value
        }
    }

    private struct Value: Decodable {
        let generatedAt: String
        let expiresAt: String
        let demo: Bool
        let matrix: [String]
    }
}
