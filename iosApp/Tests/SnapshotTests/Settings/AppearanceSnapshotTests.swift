import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The appearance page as iOS has it (`AppearanceSamples` with iOS's capabilities: no wallpaper colour, no black background,
/// the widget sample): the defaults, an own colour at the vibrant palette and high contrast with its colour well, and the monochrome palette with
/// the colours dimmed, and the dark theme picked (the sample, a widget, still shows both).
@MainActor
final class AppearanceSnapshotTests: XCTestCase {
    private let samples = AppearanceSamples.shared

    func testDefaults() {
        assertPage(samples.state(theme: SettingsFixtures.localSettings().theme, ios: true), named: "default", ax1: true)
    }

    func testOwnColour() {
        assertPage(samples.state(theme: samples.CUSTOM, ios: true), named: "custom")
    }

    func testMonochrome() {
        assertPage(samples.state(theme: samples.MONOCHROME, ios: true), named: "monochrome")
    }

    func testDarkMode() {
        assertPage(samples.state(theme: samples.DARK, ios: true), named: "dark-mode")
    }

    /// The whole page: it scrolls, so each appearance gets a height that holds it.
    private func assertPage(
        _ state: AppearanceUiState,
        named name: String,
        ax1: Bool = false,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = NavigationStack {
            AppearanceContent(state: state)
                .itmoDetailHeader(title: AppStrings.string("settings_appearance_title"), subtitle: nil)
        }
        assertAppearances(
            of: view, named: name, appearances: [.light, .dark], height: 1400, file: file, testName: testName, line: line
        )
        if ax1 {
            assertAppearances(
                of: view, named: name, appearances: [.lightAX1, .darkAX1], height: 2600, file: file, testName: testName,
                line: line
            )
        }
    }
}
