@testable import ITMOWidgets
import SwiftUI
import XCTest

/// The QR modules are one solid area: where a light module's corner is filled between three dark ones, no light
/// seam shows along the cell borders, whichever way the fillet faces. Modules of 7.3 points put every border inside a
/// pixel, where a seam would show.
@MainActor
final class QrShapesTests: XCTestCase {
    func testFilletsJoinTheirDarkNeighboursWithoutSeams() throws {
        // A light module ringed by dark ones: a fillet in each of its four corners.
        let matrix = [[true, true, true], [true, false, true], [true, true, true]]
        let side: CGFloat = 7.3 * 3
        let view = Rectangle().fill(.white)
            .overlay { QrModulesShape(matrix: matrix).fill(.black) }
            .frame(width: side, height: side)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let image = try XCTUnwrap(renderer.cgImage)
        let pixels = try rgba(of: image)
        // Pixels on the light module's borders, next to each corner, inside the fillets.
        for (x, y) in [(8, 7), (13, 7), (8, 14), (13, 14), (7, 8), (7, 13), (14, 8), (14, 13)] {
            let red = pixels[(y * image.width + x) * 4]
            XCTAssertLessThan(red, 24, "pixel (\(x), \(y))")
        }
    }

    private func rgba(of image: CGImage) throws -> [UInt8] {
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let context = try XCTUnwrap(CGContext(
            data: &pixels, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: image.width * 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return pixels
    }
}
