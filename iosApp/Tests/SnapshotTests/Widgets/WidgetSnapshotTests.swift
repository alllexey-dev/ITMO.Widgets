import SwiftUI
import WidgetKit
import XCTest
@testable import ITMOWidgets

/// The widget entry views at their family size: the QR widget (IO-10a) in all four appearances with Android's default
/// options, without dynamic colours and on a tinted home screen (IO-FIX-QRW), the lesson and the day widget (IO-10b)
/// light and dark, AX1 where a family has the least room, and all three in the app's theme (DS-ACC3, the teal preset).
/// WidgetKit's container background is ignored outside a widget, so each entry view paints its own tile; the home
/// screen clips the corners.
final class WidgetSnapshotTests: XCTestCase {
    /// The fixture's generation time: 2026-09-01T08:00:30Z, so the spoiler's noise never changes.
    private let date = Date(timeIntervalSince1970: 1_788_249_630)

    func testQrSpoiler() {
        assertQr(.spoiler, named: "spoiler")
    }

    func testQrRevealed() throws {
        assertQr(.revealed(matrix: try fixtureMatrix(), demo: false), named: "revealed")
    }

    func testQrRevealedInDemo() throws {
        assertQr(.revealed(matrix: try fixtureMatrix(), demo: true), named: "demo")
    }

    /// Dynamic colours off: the code and the spoiler are black on white in both themes, Android's static colours.
    func testQrWithoutDynamicColours() throws {
        let off = QrWidgetAppearance(spoiler: true, dynamicColors: false, animation: .circle)
        assertQr(.spoiler, appearance: off, named: "spoiler", appearances: [.light, .dark])
        assertQr(
            .revealed(matrix: try fixtureMatrix(), demo: false), appearance: off, named: "revealed",
            appearances: [.light, .dark]
        )
    }

    /// The tinted home screen keeps only alpha: the code is holes in a light plate, without the tile's background.
    func testQrRevealedOnATintedHomeScreen() throws {
        assertQr(
            .revealed(matrix: try fixtureMatrix(), demo: false),
            renderingMode: .accented,
            named: "accented",
            appearances: [.dark]
        )
    }

    /// `settings_widgets_theme_title` with the teal preset: the code and the spoiler in the app's palette.
    func testQrInTheAppTheme() throws {
        var themed = QrWidgetAppearance.standard
        themed.palette = WidgetPaletteFixtures.teal
        assertQr(.spoiler, appearance: themed, named: "spoiler", appearances: [.light, .dark])
        assertQr(
            .revealed(matrix: try fixtureMatrix(), demo: false), appearance: themed, named: "revealed",
            appearances: [.light, .dark]
        )
    }

    func testQrExpired() {
        assertQr(.expired, named: "expired")
    }

    func testQrSignedOut() {
        assertQr(.signedOut, named: "signed-out")
    }

    // MARK: Lesson widget

    func testLessonCurrent() throws {
        try assertLesson(.snapshot(fixture(at: 0)), family: .systemSmall, named: "current", appearances: SnapshotAppearance.all)
        try assertLesson(.snapshot(fixture(at: 0)), family: .systemMedium, named: "current-medium")
    }

    func testLessonInTheAppTheme() throws {
        try assertLesson(
            .snapshot(fixture(at: 0)), family: .systemSmall, palette: WidgetPaletteFixtures.teal, named: "current"
        )
    }

    func testLessonInABreak() throws {
        try assertLesson(.snapshot(fixture(at: 2)), family: .systemSmall, named: "break")
    }

    func testLessonPendingSport() throws {
        try assertLesson(.snapshot(fixture(at: 5)), family: .systemMedium, named: "pending")
    }

    func testLessonEmptyDay() {
        assertLesson(.snapshot(emptyDay), family: .systemSmall, named: "empty")
    }

    func testLessonSignedOut() {
        assertLesson(.signedOut, family: .systemSmall, named: "signed-out")
    }

    func testLessonDemo() {
        assertLesson(.demo, family: .systemSmall, named: "demo")
    }

    func testLessonUnavailable() {
        assertLesson(.unavailable, family: .systemSmall, named: "unavailable")
    }

    func testLessonLockScreen() throws {
        try assertLesson(.snapshot(fixture(at: 0)), family: .accessoryRectangular, named: "rectangular")
        try assertLesson(.snapshot(fixture(at: 0)), family: .accessoryInline, named: "inline")
    }

    func testLessonLongNamesLargeTextOnTheNarrowestPhone() {
        assertLesson(.snapshot(longNames), family: .systemMedium, size: WidgetSizes.narrowMedium, named: "long")
    }

    // MARK: Day widget

    func testDayCurrent() throws {
        try assertDay(.snapshot(fixture(at: 0)), family: .systemMedium, named: "current-medium")
        try assertDay(.snapshot(fixture(at: 0)), family: .systemLarge, named: "current", appearances: SnapshotAppearance.all)
    }

    func testDayInTheAppTheme() throws {
        try assertDay(
            .snapshot(fixture(at: 0)), family: .systemMedium, palette: WidgetPaletteFixtures.teal, named: "current-medium"
        )
    }

    func testDayInABreak() throws {
        try assertDay(.snapshot(fixture(at: 2)), family: .systemLarge, named: "break")
    }

    func testDayPendingSport() throws {
        try assertDay(.snapshot(fixture(at: 5)), family: .systemLarge, named: "pending")
    }

    func testDayTomorrowAfterToday() throws {
        try assertDay(.snapshot(fixture(at: 6)), family: .systemLarge, named: "tomorrow")
    }

    func testDayEmpty() throws {
        try assertDay(.snapshot(fixture(at: 9)), family: .systemMedium, named: "empty")
    }

    func testDaySignedOut() {
        assertDay(.signedOut, family: .systemMedium, named: "signed-out")
    }

    func testDayDemo() {
        assertDay(.demo, family: .systemMedium, named: "demo")
    }

    func testDayLongNamesLargeTextOnTheNarrowestPhone() {
        assertDay(.snapshot(longNames), family: .systemMedium, size: WidgetSizes.narrowMedium, named: "long")
    }

    private func assertLesson(
        _ content: LessonWidgetContent,
        family: WidgetFamily,
        size: CGSize? = nil,
        palette: WidgetPalette? = nil,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let size = size ?? Self.size(of: family)
        assertAppearances(
            of: SingleLessonEntryView(
                entry: LessonWidgetEntry(date: date, content: content, palette: palette), family: family
            ),
            named: name,
            appearances: appearances,
            width: size.width,
            height: size.height,
            file: file,
            testName: testName,
            line: line
        )
    }

    private func assertDay(
        _ content: LessonWidgetContent,
        family: WidgetFamily,
        size: CGSize? = nil,
        palette: WidgetPalette? = nil,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let size = size ?? Self.size(of: family)
        assertAppearances(
            of: DayScheduleEntryView(
                entry: LessonWidgetEntry(date: date, content: content, palette: palette), family: family
            ),
            named: name,
            appearances: appearances,
            width: size.width,
            height: size.height,
            file: file,
            testName: testName,
            line: line
        )
    }

    private static func size(of family: WidgetFamily) -> CGSize {
        switch family {
        case .systemSmall: WidgetSizes.small
        case .systemLarge: WidgetSizes.large
        case .accessoryRectangular: WidgetSizes.accessoryRectangular
        case .accessoryInline: WidgetSizes.accessoryInline
        default: WidgetSizes.medium
        }
    }

    /// Entry `index` of LS-3's reference timeline (`shared/feature-schedule/fixtures`), read from the source tree in
    /// the App Group envelope as the references are: 0 the first lesson now, 2 a break, 5 a pending sport row next,
    /// 6 tomorrow after today, 9 nothing today or tomorrow.
    private func fixture(at index: Int) throws -> LessonWidgetSnapshot {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../../../../shared/feature-schedule/fixtures/schedule-widget-timeline-v1.json")
            .standardized
        let value = try String(contentsOf: url, encoding: .utf8)
        let timeline = try XCTUnwrap(LessonTimeline.decode(Data(#"{"version": 1, "value": \#(value)}"#.utf8)))
        return timeline.entries[index].snapshot
    }

    /// A day without lessons, as the selector writes it.
    private var emptyDay: LessonWidgetSnapshot {
        LessonWidgetSnapshot(
            singleLesson: SingleLessonContent(kind: .emptyToday, lesson: nil, remainingLessons: 0),
            lessonList: [
                LessonListItem(kind: .header, lesson: nil, dateIso: "2026-09-01", tomorrow: false),
                LessonListItem(kind: .emptyToday, lesson: nil, dateIso: nil, tomorrow: false),
            ],
            singleLessonStyle: .dot,
            lessonListStyle: .dot,
            compactTextSize: nil,
            fullTextSize: nil
        )
    }

    /// The longest names, the line marker and the largest text option.
    private var longNames: LessonWidgetSnapshot {
        func lesson(_ state: WidgetLesson.State, start: String, end: String, typeId: Int) -> WidgetLesson {
            WidgetLesson(
                subject: SnapshotFixtures.longSubjectName, start: start, end: end, typeId: typeId,
                teacher: SnapshotFixtures.longPersonName, room: "1506/1", building: "Кронва", state: state,
                pendingStatus: nil
            )
        }
        let current = lesson(.current, start: "10:00", end: "11:30", typeId: 3)
        return LessonWidgetSnapshot(
            singleLesson: SingleLessonContent(kind: .lesson, lesson: current, remainingLessons: 3),
            lessonList: [
                LessonListItem(kind: .header, lesson: nil, dateIso: "2026-09-01", tomorrow: false),
                LessonListItem(kind: .lesson, lesson: lesson(.completed, start: "08:20", end: "09:50", typeId: 1), dateIso: nil, tomorrow: false),
                LessonListItem(kind: .lesson, lesson: current, dateIso: nil, tomorrow: false),
                LessonListItem(kind: .lesson, lesson: lesson(.upcoming, start: "11:40", end: "13:10", typeId: 6), dateIso: nil, tomorrow: false),
                LessonListItem(kind: .end, lesson: nil, dateIso: nil, tomorrow: false),
            ],
            singleLessonStyle: .line,
            lessonListStyle: .line,
            compactTextSize: .extraLarge,
            fullTextSize: .extraLarge
        )
    }

    private func assertQr(
        _ content: QrWidgetContent,
        appearance: QrWidgetAppearance = .standard,
        renderingMode: WidgetRenderingMode = .fullColor,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        assertAppearances(
            of: QrWidgetEntryView(entry: QrWidgetEntry(date: date, content: content, appearance: appearance))
                .environment(\.widgetRenderingMode, renderingMode),
            named: name,
            appearances: appearances,
            width: WidgetSizes.small.width,
            height: WidgetSizes.small.height,
            file: file,
            testName: testName,
            line: line
        )
    }

    /// The demo pass the shared writer writes (`iosApp/Tests/UnitTests/Fixtures/qr-pass-v1.json`), read from the
    /// source tree as the references are.
    private func fixtureMatrix() throws -> [[Bool]] {
        let fixture = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../../UnitTests/Fixtures/qr-pass-v1.json")
            .standardized
        return try XCTUnwrap(QrPassSnapshot.decode(Data(contentsOf: fixture))).matrix
    }
}

/// The teal preset's widget palette, `AccentColor.TEAL.widgetPalette()` of `:shared:designsystem`
/// (`WidgetPalettesTest` pins the same values).
enum WidgetPaletteFixtures {
    static let teal = WidgetPalette(
        light: WidgetColorRoles(
            surface: 0xF4FBF8, surfaceContainer: 0xE9EFED, onSurface: 0x161D1C,
            onSurfaceVariant: 0x3F4947, outlineVariant: 0xBEC9C6, primary: 0x006A60
        ),
        dark: WidgetColorRoles(
            surface: 0x0E1513, surfaceContainer: 0x1A2120, onSurface: 0xDDE4E1,
            onSurfaceVariant: 0xBEC9C6, outlineVariant: 0x3F4947, primary: 0x82D5C8
        )
    )
}
