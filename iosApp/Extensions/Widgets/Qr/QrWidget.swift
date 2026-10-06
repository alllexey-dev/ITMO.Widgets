import SwiftUI
import WidgetKit

/// The home-screen QR pass (master A9): small, one configuration for every placed widget, because Android keeps the
/// widget options global and they reach the extension in the pass snapshot, not through `AppIntentConfiguration`.
struct QrWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name.
    static let kind = "dev.alllexey.itmowidgets.widget.qr"

    /// Where a tap outside the spoiler button goes: the QR pass above home (`RouteURL`, id `qr_pass`).
    static let passURL = URL(string: "itmowidgets://route/qr_pass")!

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: QrWidgetProvider()) { entry in
            QrWidgetEntryView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetQrCodeName))
        .description(Text(.widgetQrCodeDescription))
        .supportedFamilies([.systemSmall])
        .contentMarginsDisabled()
    }
}

/// Reads the App Group files on every request; it links no Kotlin and makes no network call.
struct QrWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> QrWidgetEntry {
        QrWidgetEntry(date: Date(), content: .spoiler)
    }

    /// The gallery shows the spoiler, never a real code.
    func getSnapshot(in context: Context, completion: @escaping (QrWidgetEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
        } else {
            completion(timeline(now: Date()).entries.first ?? placeholder(in: context))
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QrWidgetEntry>) -> Void) {
        completion(timeline(now: Date()))
    }

    private func timeline(now: Date) -> Timeline<QrWidgetEntry> {
        let container = AppGroupSnapshot.container()
        return QrWidgetTimeline.timeline(
            now: now,
            session: SessionFile.read(fromContainer: container),
            pass: QrPassSnapshot.read(fromContainer: container),
            reveal: QrWidgetReveal.read(fromContainer: container)
        )
    }
}
