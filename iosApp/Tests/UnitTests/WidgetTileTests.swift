@testable import ITMOWidgets
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
}
