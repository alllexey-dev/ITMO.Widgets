import Foundation
import WidgetKit

/// What the QR widget shows at one moment.
enum QrWidgetContent: Equatable {
    /// No session file: signed out, or a build without an App Group container.
    case signedOut
    /// A valid pass behind the spoiler; a tap reveals it (`RevealQrIntent`).
    case spoiler
    /// The pass itself: `matrix` rows from the top, `true` dark; `demo` marks the demo session's code.
    case revealed(matrix: [[Bool]], demo: Bool)
    /// Signed in without a valid pass (none yet, or past its deadline); a tap opens the QR pass in the app.
    case expired
}

struct QrWidgetEntry: TimelineEntry, Equatable {
    let date: Date
    let content: QrWidgetContent
    /// The options the pass was written with: the colours, and how the code appears after the spoiler.
    var appearance: QrWidgetAppearance = .standard
}

/// The QR widget's timeline from its App Group files. Swift computes no time of its own here: the pass's deadline
/// comes from the app, the reveal's end from `QrWidgetReveal`; WidgetKit only switches between the entries.
enum QrWidgetTimeline {
    /// The widget asks for a new timeline at least this often, Android's widget update period. Every new pass
    /// reloads it sooner (`QrPassSnapshotWriter`).
    static let refreshInterval: TimeInterval = 60 * 60

    /// The entries from `now`: the current state, then the moment the reveal ends and the moment the pass expires.
    static func entries(now: Date, session: SessionFile?, pass: QrPassSnapshot?, reveal: QrWidgetReveal?)
        -> [QrWidgetEntry] {
        guard session != nil else { return [QrWidgetEntry(date: now, content: .signedOut)] }
        guard let pass, pass.expiresAt > now else { return [QrWidgetEntry(date: now, content: .expired)] }

        func entry(_ date: Date, _ content: QrWidgetContent) -> QrWidgetEntry {
            QrWidgetEntry(date: date, content: content, appearance: pass.appearance)
        }
        let code = QrWidgetContent.revealed(matrix: pass.matrix, demo: pass.demo)
        var entries: [QrWidgetEntry]
        if !pass.appearance.spoiler {
            entries = [entry(now, code)]
        } else if let revealedUntil = reveal?.revealedUntil, revealedUntil > now {
            entries = [entry(now, code)]
            if revealedUntil < pass.expiresAt {
                entries.append(entry(revealedUntil, .spoiler))
            }
        } else {
            entries = [entry(now, .spoiler)]
        }
        entries.append(entry(pass.expiresAt, .expired))
        return entries
    }

    static func timeline(now: Date, session: SessionFile?, pass: QrPassSnapshot?, reveal: QrWidgetReveal?)
        -> Timeline<QrWidgetEntry> {
        Timeline(
            entries: entries(now: now, session: session, pass: pass, reveal: reveal),
            policy: .after(now.addingTimeInterval(refreshInterval))
        )
    }
}
