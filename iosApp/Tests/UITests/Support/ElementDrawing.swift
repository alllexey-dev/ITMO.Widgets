import XCTest

extension XCUIElement {
    /// Whether the element's pixels on screen hold anything besides its background: an element can be hittable while
    /// nothing of it is drawn (a blank week strip whose days still take taps).
    var isDrawn: Bool {
        guard let image = screenshot().image.cgImage else { return false }
        let width = image.width, height = image.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return false }
        // The corner pixel is the background: a day's selection is rounded and its text sits in the middle.
        let background = (Int(pixels[0]), Int(pixels[1]), Int(pixels[2]))
        var differing = 0
        for index in stride(from: 0, to: pixels.count, by: 4) {
            let distance = abs(Int(pixels[index]) - background.0) + abs(Int(pixels[index + 1]) - background.1)
                + abs(Int(pixels[index + 2]) - background.2)
            if distance > 96 { differing += 1 }
        }
        // A day's weekday and number cover several percent of it; a blank one differs in no pixel.
        return differing * 200 > width * height
    }

    /// Waits until the element is drawn (`isDrawn`).
    func waitUntilDrawn(timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if exists && isDrawn { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        return exists && isDrawn
    }
}
