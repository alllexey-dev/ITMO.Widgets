import XCTest

extension XCUIElement {
    /// Whether the element's pixels on screen hold anything besides its background: an element can be hittable while
    /// nothing of it is drawn (a blank week strip whose days still take taps).
    var isDrawn: Bool {
        screenshot().image.cgImage.map(ElementDrawing.holdsDrawing) ?? false
    }

    /// Whether the element is on screen to tap and drawn there: the check a scroll loop stops on. `isDrawn` alone
    /// would screenshot an element off the screen.
    var isShown: Bool {
        isHittable && isDrawn
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

extension XCUIApplication {
    /// Whether `screenshot`'s pixels in `frame` (points, as element frames give them) hold anything besides the
    /// background: for what XCUITest does not list, such as the system refresh control.
    func isDrawn(in frame: CGRect, of screenshot: XCUIScreenshot) -> Bool {
        crop(screenshot, to: frame).map(ElementDrawing.holdsDrawing) ?? false
    }

    /// Whether the pixels in `frame` (points) differ between two screenshots.
    func differs(in frame: CGRect, between first: XCUIScreenshot, and second: XCUIScreenshot) -> Bool {
        guard let firstImage = crop(first, to: frame), let secondImage = crop(second, to: frame),
              let one = ElementDrawing.rgba(firstImage), let other = ElementDrawing.rgba(secondImage),
              one.count == other.count else { return true }
        var changed = 0
        for index in stride(from: 0, to: one.count, by: 4) {
            var distance = 0
            for channel in 0..<3 { distance += abs(Int(one[index + channel]) - Int(other[index + channel])) }
            if distance > 96 { changed += 1 }
        }
        // As in `isDrawn`: more than half a percent of the pixels.
        return changed * 200 > one.count / 4
    }

    private func crop(_ screenshot: XCUIScreenshot, to frame: CGRect) -> CGImage? {
        guard let image = screenshot.image.cgImage, self.frame.width > 0 else { return nil }
        let scale = CGFloat(image.width) / self.frame.width
        let pixels = CGRect(x: frame.minX * scale, y: frame.minY * scale, width: frame.width * scale, height: frame.height * scale)
        return image.cropping(to: pixels.integral)
    }
}

private enum ElementDrawing {
    /// The image as RGBA bytes; nil when it cannot be drawn.
    static func rgba(_ image: CGImage) -> [UInt8]? {
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
        return drawn ? pixels : nil
    }

    static func holdsDrawing(_ image: CGImage) -> Bool {
        let width = image.width, height = image.height
        guard let pixels = rgba(image) else { return false }
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
}
