@testable import ITMOWidgets
import XCTest

/// The QR widget's look for each of its options (IO-FIX-QRW): dynamic colours in both themes, the spoiler's colours,
/// the reveal animation and the tinted home screen.
final class QrWidgetStyleTests: XCTestCase {
    private let date = Date(timeIntervalSince1970: 1_788_250_000)
    private let code = QrWidgetContent.revealed(matrix: [[true, false], [false, true]], demo: false)

    func testWithoutDynamicColoursTheCodeIsBlackOnWhiteInBothThemes() {
        let blackOnWhite = QrWidgetTile(backgroundRGB: 0xFFFFFF, foregroundRGB: 0x000000)
        XCTAssertEqual(style(code, options(dynamicColors: false), dark: false).tile, blackOnWhite)
        XCTAssertEqual(style(code, options(dynamicColors: false), dark: true).tile, blackOnWhite)
    }

    func testDynamicColoursTakeTheAppSchemeDarkOnLightInBothThemes() {
        // Light: surface behind onSurface, the darker of onSurfaceVariant and onSurface.
        XCTAssertEqual(
            style(code, .standard, dark: false).tile,
            QrWidgetTile(backgroundRGB: ItmoColorRoles.light.surface, foregroundRGB: ItmoColorRoles.light.onSurface)
        )
        // Dark: the dark surface swaps with onSurfaceVariant, so the code stays dark on light for a scanner.
        let dark = style(code, .standard, dark: true).tile
        let swapped = QrWidgetTile(
            backgroundRGB: ItmoColorRoles.dark.onSurfaceVariant, foregroundRGB: ItmoColorRoles.dark.surface
        )
        XCTAssertEqual(dark, swapped)
        XCTAssertNotEqual(dark, style(code, .standard, dark: false).tile)
    }

    func testTheAppPaletteReplacesTheBrandSchemeOnlyWithDynamicColours() {
        let palette = WidgetPalette(
            light: roles(surface: 0xF4FBF8, onSurfaceVariant: 0x3F4947, onSurface: 0x161D1C),
            dark: roles(surface: 0x0E1513, onSurfaceVariant: 0xBEC9C6, onSurface: 0xDDE4E1)
        )
        var themed = options()
        themed.palette = palette
        XCTAssertEqual(
            style(code, themed, dark: false).tile,
            QrWidgetTile(backgroundRGB: 0xF4FBF8, foregroundRGB: 0x161D1C)
        )
        // Dark: the palette's dark surface swaps with onSurfaceVariant, as the brand scheme does.
        XCTAssertEqual(
            style(code, themed, dark: true).tile,
            QrWidgetTile(backgroundRGB: 0xBEC9C6, foregroundRGB: 0x0E1513)
        )
        // Without dynamic colours the code stays black on white whatever the theme option says.
        var plain = options(dynamicColors: false)
        plain.palette = palette
        XCTAssertEqual(style(code, plain, dark: true).tile, .code)
    }

    func testTheResolverMatchesTheKotlinMath() {
        // A light surface keeps its place; the darker of the two text roles draws the modules.
        XCTAssertEqual(
            QrWidgetTile.resolve(surface: 0xF9F9FF, onSurfaceVariant: 0x44474E, onSurface: 0x1A1C20),
            QrWidgetTile(backgroundRGB: 0xF9F9FF, foregroundRGB: 0x1A1C20)
        )
        // A dark surface swaps with onSurfaceVariant; a lighter onSurface does not replace the modules.
        XCTAssertEqual(
            QrWidgetTile.resolve(surface: 0x111318, onSurfaceVariant: 0xC4C6D0, onSurface: 0xE2E2E9),
            QrWidgetTile(backgroundRGB: 0xC4C6D0, foregroundRGB: 0x111318)
        )
        // A tie keeps onSurfaceVariant.
        XCTAssertEqual(
            QrWidgetTile.resolve(surface: 0xFFFFFF, onSurfaceVariant: 0x202020, onSurface: 0x202020),
            QrWidgetTile(backgroundRGB: 0xFFFFFF, foregroundRGB: 0x202020)
        )
    }

    func testTheSpoilerTakesTheCodesColours() {
        for appearance in [QrWidgetAppearance.standard, options(dynamicColors: false)] {
            for dark in [false, true] {
                XCTAssertEqual(style(.spoiler, appearance, dark: dark).tile, style(code, appearance, dark: dark).tile)
            }
        }
    }

    func testTheStatesWithoutACodeFollowTheSystemTheme() {
        for content in [QrWidgetContent.signedOut, .expired] {
            XCTAssertEqual(style(content, .standard, dark: false).tile, .light)
            XCTAssertEqual(style(content, .standard, dark: true).tile, .dark)
        }
    }

    func testTheRevealFadesUnlessTheAnimationIsOff() {
        XCTAssertEqual(style(code, options(animation: .circle)).revealTransition, .fade)
        XCTAssertEqual(style(code, options(animation: .fade)).revealTransition, .fade)
        XCTAssertEqual(style(code, options(animation: .none)).revealTransition, .none)
    }

    func testATintedHomeScreenDrawsNoBackground() {
        XCTAssertTrue(style(code, .standard).fullColor)
        let tinted = QrWidgetStyle(entry: QrWidgetEntry(date: date, content: code), dark: true, fullColor: false)
        XCTAssertFalse(tinted.fullColor)
    }

    func testThePlateLeavesTheDarkModulesEmpty() {
        let matrix = [[true, false], [false, false]]
        let path = QrPlateShape(matrix: matrix).path(in: CGRect(x: 0, y: 0, width: 60, height: 60))
        // Six modules across: the 2 x 2 code inside a quiet zone of two on each side, 10 pt each.
        XCTAssertFalse(path.contains(CGPoint(x: 25, y: 25), eoFill: true), "a dark module is a hole")
        XCTAssertTrue(path.contains(CGPoint(x: 35, y: 25), eoFill: true), "a light module is plate")
        XCTAssertTrue(path.contains(CGPoint(x: 5, y: 30), eoFill: true), "the quiet zone is plate")
    }

    private func style(_ content: QrWidgetContent, _ appearance: QrWidgetAppearance, dark: Bool = false)
        -> QrWidgetStyle {
        let entry = QrWidgetEntry(date: date, content: content, appearance: appearance)
        return QrWidgetStyle(entry: entry, dark: dark, fullColor: true)
    }

    private func roles(surface: UInt32, onSurfaceVariant: UInt32, onSurface: UInt32) -> WidgetColorRoles {
        WidgetColorRoles(
            surface: surface, surfaceContainer: surface, onSurface: onSurface,
            onSurfaceVariant: onSurfaceVariant, outlineVariant: onSurfaceVariant, primary: onSurface
        )
    }

    private func options(dynamicColors: Bool = true, animation: QrRevealAnimation = .circle) -> QrWidgetAppearance {
        QrWidgetAppearance(spoiler: true, dynamicColors: dynamicColors, animation: animation)
    }
}
