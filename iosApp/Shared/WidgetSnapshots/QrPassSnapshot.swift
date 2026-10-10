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
    /// The QR widget options the app wrote beside the pass; Android's defaults for any field the file lacks.
    var appearance: QrWidgetAppearance = .standard

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("qr-pass", version: version)

    /// The highest snapshot version this build reads.
    static let version = 1

    /// The snapshot in `data`, or nil when it is corrupt, not square, or written by a newer version: the reader then
    /// shows its placeholder (ADR 0027).
    static func decode(_ data: Data) -> QrPassSnapshot? {
        AppGroupSnapshot.decode(Value.self, from: data, maxVersion: version).flatMap(snapshot)
    }

    /// The snapshot in the App Group `container`, or nil when it is missing or unreadable (signed out).
    static func read(fromContainer container: URL?) -> QrPassSnapshot? {
        AppGroupSnapshot.read(Value.self, file: fileName, maxVersion: version, in: container).flatMap(snapshot)
    }

    private static func snapshot(_ value: Value) -> QrPassSnapshot? {
        let matrix = value.matrix.map { row in row.map { $0 == "1" } }
        guard !matrix.isEmpty, matrix.allSatisfy({ $0.count == matrix.count }),
              value.matrix.allSatisfy({ row in row.allSatisfy { $0 == "0" || $0 == "1" } })
        else { return nil }
        return QrPassSnapshot(
            generatedAt: value.generatedAt,
            expiresAt: value.expiresAt,
            demo: value.demo,
            matrix: matrix,
            appearance: QrWidgetAppearance(
                spoiler: value.spoiler ?? QrWidgetAppearance.standard.spoiler,
                dynamicColors: value.dynamicColors ?? QrWidgetAppearance.standard.dynamicColors,
                animation: QrRevealAnimation(name: value.animation)
            )
        )
    }

    private struct Value: Decodable {
        let generatedAt: Date
        let expiresAt: Date
        let demo: Bool
        let matrix: [String]
        let spoiler: Bool?
        let dynamicColors: Bool?
        let animation: String?
    }
}
