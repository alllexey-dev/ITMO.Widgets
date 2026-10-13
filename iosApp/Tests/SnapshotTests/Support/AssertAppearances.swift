import SnapshotTesting
import SwiftUI
import UIKit
import XCTest

/// One rendering condition of the snapshot matrix: colour scheme and Dynamic Type size.
struct SnapshotAppearance {
    let name: String
    let colorScheme: ColorScheme
    let dynamicTypeSize: DynamicTypeSize

    static let light = SnapshotAppearance(name: "light", colorScheme: .light, dynamicTypeSize: .large)
    static let dark = SnapshotAppearance(name: "dark", colorScheme: .dark, dynamicTypeSize: .large)
    static let lightAX1 = SnapshotAppearance(name: "light-ax1", colorScheme: .light, dynamicTypeSize: .accessibility1)
    static let darkAX1 = SnapshotAppearance(name: "dark-ax1", colorScheme: .dark, dynamicTypeSize: .accessibility1)

    /// The matrix every snapshot test records: Dynamic Type L (the system default) and AX1, each light and dark.
    static let all: [SnapshotAppearance] = [.light, .dark, .lightAX1, .darkAX1]

    var traits: UITraitCollection {
        UITraitCollection(mutations: { traits in
            traits.userInterfaceStyle = colorScheme == .dark ? .dark : .light
            traits.preferredContentSizeCategory = UIContentSizeCategory(dynamicTypeSize)
            traits.displayScale = SnapshotMatrix.displayScale
        })
    }
}

/// The settings every snapshot shares, set once here and never per test.
enum SnapshotMatrix {
    /// Narrower than any supported iPhone (the smallest is 375 pt), so a layout that fits here fits everywhere.
    static let width: CGFloat = 320
    /// Fixed instead of the simulator's, so a reference does not depend on the device that recorded it.
    static let displayScale: CGFloat = 2
    /// Every pixel must match; a pixel matches within a CIE Delta E of 2 (98 %), which absorbs GPU rounding but
    /// not a changed colour.
    static let precision: Float = 1
    static let perceptualPrecision: Float = 0.98

    /// The view as the matrix renders it: the appearance in the SwiftUI environment and the UIKit traits, over the
    /// system background, `width` points wide and as tall as its content unless `height` is given.
    static func prepare<V: View>(_ view: V, _ appearance: SnapshotAppearance, width: CGFloat, height: CGFloat?)
        -> (view: AnyView, strategy: Snapshotting<AnyView, UIImage>) {
        let themed = view
            .environment(\.colorScheme, appearance.colorScheme)
            .environment(\.dynamicTypeSize, appearance.dynamicTypeSize)
            .environment(\.locale, Locale(identifier: "ru_RU"))
        let framed: AnyView
        let layout: SwiftUISnapshotLayout
        if let height {
            framed = AnyView(themed.frame(width: width, height: height).background(Color(uiColor: .systemBackground)))
            layout = .fixed(width: width, height: height)
        } else {
            framed = AnyView(
                themed
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(width: width)
                    .background(Color(uiColor: .systemBackground))
            )
            layout = .sizeThatFits
        }
        let strategy = Snapshotting<AnyView, UIImage>.image(
            precision: precision,
            perceptualPrecision: perceptualPrecision,
            layout: layout,
            traits: appearance.traits
        )
        return (framed, strategy)
    }
}

extension XCTestCase {
    /// Snapshots `view` once per appearance of the matrix into
    /// `__Snapshots__/<test file>/<test>.<name>-<appearance>.png` beside the calling test file.
    ///
    /// Recording follows `SNAPSHOT_TESTING_RECORD` (scripts/ios/test.sh sets `all` with `--record`, `never` with
    /// `--ci`); a missing reference is recorded and fails the test.
    func assertAppearances<V: View>(
        of view: V,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        width: CGFloat = SnapshotMatrix.width,
        height: CGFloat? = nil,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        for appearance in appearances {
            let prepared = SnapshotMatrix.prepare(view, appearance, width: width, height: height)
            assertSnapshot(
                of: prepared.view,
                as: prepared.strategy,
                named: "\(name)-\(appearance.name)",
                fileID: fileID,
                file: file,
                testName: testName,
                line: line,
                column: column
            )
        }
    }
}

extension XCTestCase {
    /// Snapshots `view` in a window of its own, once per appearance, into the same `__Snapshots__` files as
    /// `assertAppearances`. A layer render misses system chrome and materials (the glass tab bar and buttons, the
    /// navigation bar, the system's unavailable view, sheets with their dimming); the window draws them as the screen
    /// shows them. Without a `height` the window is as tall as the content (no scroll view at the root then).
    /// `settle` waits for presentations and the bars' first layout.
    @MainActor
    func assertScreenAppearances<V: View>(
        of view: V,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        width: CGFloat = SnapshotMatrix.width,
        height: CGFloat? = SnapshotMatrix.screenHeight,
        settle: TimeInterval = 1,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        for appearance in appearances {
            let image = SnapshotMatrix.renderInWindow(view, appearance, width: width, height: height, settle: settle)
            assertSnapshot(
                of: image,
                as: .image(
                    precision: SnapshotMatrix.windowPrecision, perceptualPrecision: SnapshotMatrix.perceptualPrecision
                ),
                named: "\(name)-\(appearance.name)",
                fileID: fileID,
                file: file,
                testName: testName,
                line: line,
                column: column
            )
        }
    }
}

extension SnapshotMatrix {
    /// A whole screen at the matrix width.
    static let screenHeight: CGFloat = 640
    /// Liquid Glass samples what lies under it, and a handful of pixels on its rim come out a shade apart between
    /// two renders of the same window; 0.01 % of the pixels may differ, a changed text or icon never fits in that.
    static let windowPrecision: Float = 0.9999

    /// `view` in a window above the host app, in `appearance`, drawn with its system chrome.
    @MainActor
    static func renderInWindow<V: View>(
        _ view: V, _ appearance: SnapshotAppearance, width: CGFloat, height: CGFloat?, settle: TimeInterval
    ) -> UIImage {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else {
            preconditionFailure("the snapshot tests run hosted in the app, which has a window scene")
        }
        let themed = view
            .environment(\.locale, Locale(identifier: "ru_RU"))
            .environment(\.dynamicTypeSize, appearance.dynamicTypeSize)
            .background(Color(uiColor: .systemBackground))
        let host = UIHostingController(rootView: themed)
        let window = UIWindow(windowScene: scene)
        window.windowLevel = .alert + 1
        window.overrideUserInterfaceStyle = appearance.colorScheme == .dark ? .dark : .light
        window.traitOverrides.preferredContentSizeCategory = UIContentSizeCategory(appearance.dynamicTypeSize)
        window.frame = CGRect(x: 0, y: 0, width: width, height: height ?? screenHeight)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            // Takes the window and its presentations down before the next test, so nothing outlives the test.
            host.dismiss(animated: false)
            window.isHidden = true
            window.rootViewController = nil
            window.windowScene = nil
            RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.2))
        }
        if height == nil {
            let fitted = host.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude))
            window.frame.size.height = ceil(fitted.height)
        }
        RunLoop.main.run(until: Date(timeIntervalSinceNow: settle))
        let format = UIGraphicsImageRendererFormat()
        format.scale = displayScale
        // 8-bit sRGB like the layer renders: the simulator's wide-colour default keeps sub-pixel noise of the glass.
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
    }
}
