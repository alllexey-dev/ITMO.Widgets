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
