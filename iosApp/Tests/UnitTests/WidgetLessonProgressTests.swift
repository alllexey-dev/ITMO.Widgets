@testable import ITMOWidgets
import SwiftUI
import UIKit
import XCTest

/// Placed widgets and the gallery use the same progress view. These are hosted render tests, not WidgetKit archives.
@MainActor
final class WidgetLessonProgressTests: XCTestCase {
    func testBarRendersBeforeDuringAndAfterTheLesson() async throws {
        try await assertProgress(ring: false, size: CGSize(width: 140, height: 30))
    }

    func testRingRendersBeforeDuringAndAfterTheLesson() async throws {
        try await assertProgress(ring: true, size: CGSize(width: 60, height: 60))
    }

    func testProgressIsBoundedAtLessonTransitions() {
        let start = Date(timeIntervalSince1970: 1_792_991_400)
        let end = start.addingTimeInterval(5_400)
        for (offset, expected) in [(-60.0, 0.0), (0, 0), (1_440, 24.0 / 90), (5_400, 1), (5_460, 1)] {
            XCTAssertEqual(
                WidgetLessonProgress.fraction(since: start, until: end, at: start.addingTimeInterval(offset)),
                expected, accuracy: 0.000_001
            )
        }
    }

    func testInvalidLessonIntervalsHaveFiniteProgress() {
        let now = Date(timeIntervalSince1970: 1_792_991_400)
        XCTAssertEqual(WidgetLessonProgress.fraction(since: now, until: now, at: now), 0)
        XCTAssertEqual(WidgetLessonProgress.fraction(since: now, until: now.addingTimeInterval(-60), at: now), 0)
    }

    private func assertProgress(ring: Bool, size: CGSize) async throws {
        let blank = try await render(Color.black, size: size)
        let now = Date(timeIntervalSince1970: 1_792_991_400)
        let intervals: [(String, TimeInterval, TimeInterval)] = [
            ("before", 600, 6_000),
            ("during", -1_440, 3_960),
            ("after", -6_000, -600),
        ]
        for (state, start, end) in intervals {
            let progress = WidgetLessonProgress(
                since: now.addingTimeInterval(start), until: now.addingTimeInterval(end), date: now, ring: ring
            )
            let image = try await render(progress, size: size)
            XCTAssertNotEqual(image, blank, "The progress is empty \(state) the lesson (ring: \(ring))")
        }
    }

    private func render<V: View>(_ view: V, size: CGSize) async throws -> Data {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first { $0.isKeyWindow }
        let root = view
            .tint(.white)
            .environment(\.colorScheme, .dark)
            .frame(width: size.width, height: size.height)
            .background(Color.black)
        let host = UIHostingController(rootView: root)
        host.safeAreaRegions = []
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: size)
        window.rootViewController = host
        window.windowLevel = .alert + 1
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            window.windowScene = nil
            previousKeyWindow?.makeKey()
        }
        // Let SwiftUI mount the progress view before drawing the window.
        try await Task.sleep(for: .milliseconds(100))
        host.view.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        var drawn = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            drawn = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        XCTAssertTrue(drawn, "The hosted progress view could not be rendered")
        return try XCTUnwrap(image.pngData())
    }
}
