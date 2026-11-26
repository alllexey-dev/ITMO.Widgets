@testable import ITMOWidgets
import Shared
import SwiftUI
import XCTest

/// A widget's tile is WidgetKit's container background alone: the entry view paints no rectangle of its own under
/// its content, which the system would show inside the content margins with corners of its own. Only a host outside
/// WidgetKit (the gallery, the snapshot tests) asks for the painted tile.
@MainActor
final class WidgetTileTests: XCTestCase {
    private let entry = ScheduleWidgetEntry(date: Date(timeIntervalSince1970: 0), content: .placeholder(.unavailable))

    func testInAWidgetTheEntryViewsPaintNoTile() throws {
        for view in views() {
            XCTAssertEqual(try cornerAlpha(of: view), 0)
        }
        XCTAssertEqual(try cornerAlpha(of: AnyView(QrWidgetEntryView(entry: QrWidgetEntry(date: entry.date, content: .expired)))), 0)
    }

    func testTheGalleryAndTheSnapshotsAskForThePaintedTile() throws {
        for view in views() {
            XCTAssertEqual(try cornerAlpha(of: AnyView(view.environment(\.paintsWidgetTile, true))), 255)
        }
    }

    private func views() -> [AnyView] {
        [
            AnyView(NextLessonEntryView(entry: entry, family: .systemSmall)),
            AnyView(DayEntryView(entry: entry)),
            AnyView(CardsEntryView(entry: entry)),
            AnyView(ClassicListEntryView(entry: entry, family: .systemMedium)),
        ]
    }

    /// The alpha of the top left pixel, far from the centred message.
    private func cornerAlpha(of view: AnyView) throws -> UInt8 {
        let renderer = ImageRenderer(content: view.frame(width: 170, height: 170))
        renderer.scale = 1
        let image = try XCTUnwrap(renderer.cgImage)
        var pixel = [UInt8](repeating: 0, count: 4)
        let context = try XCTUnwrap(CGContext(
            data: &pixel, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(image, in: CGRect(x: 0, y: 1 - image.height, width: image.width, height: image.height))
        return pixel[3]
    }

    /// A host may measure a widget without a width or a height (the Mac does), live as WidgetKit draws it: no view may
    /// lay out an infinite size, which SwiftUI aborts on ("view origin is invalid"). Also run on "My Mac (Designed for
    /// iPad)", where the system's live texts measure differently from the simulator.
    func testScheduleWidgetsLayOutLiveWithoutABound() async throws {
        let settings = ScheduleWidgetSettings(
            compact: CompactScheduleWidgetSettings(showNextLessonEarly: true, hideTeacher: false, textSize: .normal),
            full: FullScheduleWidgetSettings(
                hideTeacher: true, hidePastLessons: false, showTomorrowWhenTodayIsOver: true, textSize: .normal
            )
        )
        let file = try await IosWidgetPreviews.shared.schedule(settings: settings, moment: .lesson, theme: nil)
        let instant = Date(timeIntervalSince1970: IosWidgetPreviews.shared.seconds(moment: .lesson))
        var live = try XCTUnwrap(WidgetGalleryPreviews.scheduleEntry(Data(file.utf8), at: instant))
        live.clock = .live
        let views: [AnyView] = [
            AnyView(CardsEntryView(entry: live)), AnyView(DayEntryView(entry: live)),
            AnyView(NextLessonEntryView(entry: live, family: .systemMedium)),
            AnyView(ClassicListEntryView(entry: live, family: .systemLarge)),
        ]
        for view in views {
            for proposal in [
                ProposedViewSize(width: 344, height: 344), ProposedViewSize(width: .infinity, height: 344),
                ProposedViewSize(width: 344, height: .infinity), ProposedViewSize(width: nil, height: nil),
            ] {
                let renderer = ImageRenderer(content: view)
                renderer.proposedSize = proposal
                _ = renderer.cgImage
            }
        }
    }

    /// The cards' progress next to a text that reports an infinite ideal width, as the Mac's archived live countdown
    /// does: neither may lay out an infinite size.
    func testTheProgressNextToAnUnboundedTextStaysFinite() throws {
        let row = HStack(spacing: 12) {
            WavyProgress(value: 0.4)
            UnboundedIdeal { Color.clear }.fixedSize().finiteLayout()
        }
        for proposal in [ProposedViewSize(width: 300, height: 40), ProposedViewSize(width: .infinity, height: 40)] {
            let renderer = ImageRenderer(content: row)
            renderer.proposedSize = proposal
            XCTAssertNotNil(renderer.cgImage)
        }
    }
}

/// A view whose ideal width is infinite, like the Mac's archived live text.
private struct UnboundedIdeal: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        CGSize(width: proposal.width ?? .infinity, height: 12)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {}
}
