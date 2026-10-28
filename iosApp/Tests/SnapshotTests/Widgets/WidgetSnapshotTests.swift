import Shared
import SwiftUI
import WidgetKit
import XCTest
@testable import ITMOWidgets

/// The widget entry views at their family size, on the shared sample (`IosWidgetPreviews`: the design lab's Monday,
/// 7 September, through the real selectors and writers) at the design's moments: before the first lesson (08:05),
/// during the third (12:22), in the gap (13:40) and after the last (19:40). The next-lesson widget small and medium
/// with its Lock Screen families, the day widget large, the cards medium and large, the classic list medium and
/// large, the sport widget's states, the pass widget's options, the placeholders, and each in the app's theme (the
/// teal preset). Time is frozen at the moment, so nothing moves.
/// WidgetKit's container background is ignored outside a widget, so each entry view paints its own tile; the home
/// screen clips the corners.
@MainActor
final class WidgetSnapshotTests: XCTestCase {
    /// The fixture's generation time: 2026-09-01T08:00:30Z.
    private let date = Date(timeIntervalSince1970: 1_788_249_630)

    // MARK: Next lesson

    func testNextNow() async throws {
        let entry = try await schedule(at: .lesson)
        assertNext(entry, family: .systemSmall, named: "now", appearances: SnapshotAppearance.all)
        assertNext(entry, family: .systemMedium, named: "now-medium")
    }

    func testNextMorning() async throws {
        let entry = try await schedule(at: .morning)
        assertNext(entry, family: .systemSmall, named: "morning")
        assertNext(entry, family: .systemMedium, named: "morning-medium")
    }

    func testNextGap() async throws {
        let entry = try await schedule(at: .gap)
        assertNext(entry, family: .systemSmall, named: "gap")
        assertNext(entry, family: .systemMedium, named: "gap-medium")
    }

    func testNextTomorrow() async throws {
        let entry = try await schedule(at: .evening)
        assertNext(entry, family: .systemSmall, named: "tomorrow")
        assertNext(entry, family: .systemMedium, named: "tomorrow-medium")
    }

    func testNextInTheAppThemeWithLargeText() async throws {
        let entry = try await schedule(at: .lesson, textSize: .large, theme: Self.teal)
        assertNext(entry, family: .systemMedium, named: "now-medium")
    }

    func testNextOnTheNarrowestPhone() async throws {
        let entry = try await schedule(at: .lesson, textSize: .extraLarge)
        assertNext(entry, family: .systemMedium, size: WidgetSizes.narrowMedium, named: "now-medium")
    }

    func testNextPlaceholders() {
        assertNext(entry(.placeholder(.signedOut)), family: .systemSmall, named: "signed-out")
        assertNext(entry(.placeholder(.demo)), family: .systemSmall, named: "demo")
        assertNext(entry(.placeholder(.unavailable)), family: .systemSmall, named: "unavailable")
        assertNext(entry(.placeholder(.noLessons)), family: .systemSmall, named: "no-lessons")
    }

    func testNextLockScreen() async throws {
        let now = try await schedule(at: .lesson)
        assertNext(now, family: .accessoryRectangular, named: "rectangular")
        assertNext(now, family: .accessoryInline, named: "inline")
        assertNext(now, family: .accessoryCircular, named: "circular")
        assertNext(try await schedule(at: .morning), family: .accessoryCircular, named: "circular-morning")
    }

    // MARK: Day

    func testDayNow() async throws {
        assertDay(try await schedule(at: .lesson), named: "now", appearances: SnapshotAppearance.all)
    }

    func testDayMorning() async throws {
        assertDay(try await schedule(at: .morning), named: "morning")
    }

    func testDayTomorrow() async throws {
        assertDay(try await schedule(at: .evening), named: "done")
    }

    /// `settings_widget_hide_past_title` and the teacher on, in the app's theme on the narrowest phone.
    func testDayWithoutPastLessonsInTheAppTheme() async throws {
        let entry = try await schedule(at: .lesson, hidePast: true, teacher: true, theme: Self.teal)
        assertDay(entry, size: WidgetSizes.narrowLarge, named: "hide-past")
    }

    func testDayPlaceholders() {
        assertDay(entry(.placeholder(.signedOut)), named: "signed-out")
        assertDay(entry(.placeholder(.loading)), named: "loading")
    }

    // MARK: Cards

    func testCardsNow() async throws {
        let entry = try await schedule(at: .lesson)
        assertCards(entry, family: .systemLarge, named: "now", appearances: SnapshotAppearance.all)
        assertCards(entry, family: .systemMedium, named: "now-medium")
    }

    func testCardsTomorrowPage() async throws {
        var entry = try await schedule(at: .lesson)
        entry.cardsPage = 1
        assertCards(entry, family: .systemLarge, named: "tomorrow-page")
    }

    func testCardsMorning() async throws {
        let entry = try await schedule(at: .morning)
        assertCards(entry, family: .systemLarge, named: "morning")
        assertCards(entry, family: .systemMedium, named: "morning-medium")
    }

    func testCardsEvening() async throws {
        assertCards(try await schedule(at: .evening, teacher: true), family: .systemLarge, named: "evening")
    }

    func testCardsInTheAppThemeWithLargeText() async throws {
        let entry = try await schedule(at: .lesson, textSize: .large, teacher: true, theme: Self.teal)
        assertCards(entry, family: .systemLarge, named: "now")
    }

    // MARK: Classic list

    func testListNow() async throws {
        let entry = try await schedule(at: .lesson)
        assertList(entry, family: .systemMedium, named: "now", appearances: SnapshotAppearance.all)
        assertList(entry, family: .systemLarge, named: "now-large")
    }

    func testListTomorrow() async throws {
        assertList(try await schedule(at: .evening), family: .systemLarge, named: "tomorrow")
    }

    /// The teacher on, in the app's theme.
    func testListInTheAppTheme() async throws {
        assertList(try await schedule(at: .lesson, teacher: true, theme: Self.teal), family: .systemLarge, named: "now-large")
    }

    func testListPlaceholders() {
        assertList(entry(.placeholder(.signedOut)), family: .systemMedium, named: "signed-out")
        assertList(entry(.placeholder(.noMoreToday)), family: .systemMedium, named: "no-more-today")
    }

    // MARK: Sport

    func testSportQueue() async throws {
        let entry = try await sport(.queue)
        assertSport(entry, family: .systemMedium, named: "queue", appearances: SnapshotAppearance.all)
        assertSport(entry, family: .systemSmall, named: "queue-small")
    }

    func testSportBooked() async throws {
        assertSport(try await sport(.booked), family: .systemMedium, named: "booked")
    }

    func testSportDone() async throws {
        let entry = try await sport(.done)
        assertSport(entry, family: .systemMedium, named: "done")
        assertSport(entry, family: .systemSmall, named: "done-small")
    }

    func testSportInTheAppTheme() async throws {
        assertSport(try await sport(.queue, theme: Self.teal), family: .systemMedium, named: "queue")
    }

    func testSportPlaceholders() {
        assertSport(SportWidgetEntry(date: date, content: .placeholder(.signedOut)), family: .systemMedium, named: "signed-out")
        assertSport(SportWidgetEntry(date: date, content: .placeholder(.sportError)), family: .systemSmall, named: "error")
    }

    // MARK: Pass

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
        let off = ITMOWidgets.QrWidgetAppearance(spoiler: true, dynamicColors: false, animation: .circle)
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
        var themed = ITMOWidgets.QrWidgetAppearance.standard
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

    // MARK: Samples

    /// The sample's schedule entry at `moment`, as the gallery shows it.
    private func schedule(
        at moment: SchedulePreviewMoment,
        textSize: WidgetTextSize = .normal,
        hidePast: Bool = false,
        teacher: Bool = false,
        theme: ThemeSpec? = nil
    ) async throws -> ScheduleWidgetEntry {
        let settings = ScheduleWidgetSettings(
            compact: CompactScheduleWidgetSettings(showNextLessonEarly: true, hideTeacher: false, textSize: textSize),
            full: FullScheduleWidgetSettings(
                hideTeacher: !teacher, hidePastLessons: hidePast, showTomorrowWhenTodayIsOver: true, textSize: textSize
            )
        )
        let file = try await IosWidgetPreviews.shared.schedule(settings: settings, moment: moment, theme: theme)
        let instant = Date(timeIntervalSince1970: IosWidgetPreviews.shared.seconds(moment: moment))
        return try XCTUnwrap(WidgetGalleryPreviews.scheduleEntry(Data(file.utf8), at: instant))
    }

    /// The teal preset while the widgets follow the app's theme: its palette comes from the shared writer.
    static let teal = ThemeSpec(
        accent: .teal, customArgb: ThemeSpec.companion.DEFAULT_CUSTOM_ARGB, style: .tonalSpot, contrast: .standard,
        pureBlack: false, mode: .system
    )

    private func sport(_ state: SportWidgetSampleState, theme: ThemeSpec? = nil) async throws -> SportWidgetEntry {
        let file = try await IosWidgetPreviews.shared.sport(state: state, theme: theme)
        let moment = Date(timeIntervalSince1970: IosWidgetPreviews.shared.seconds(moment: .lesson))
        return try XCTUnwrap(WidgetGalleryPreviews.sportEntry(Data(file.utf8), at: moment))
    }

    private func entry(_ content: ScheduleWidgetContent) -> ScheduleWidgetEntry {
        ScheduleWidgetEntry(date: date, content: content, clock: .frozen(date))
    }

    private func assertNext(
        _ entry: ScheduleWidgetEntry,
        family: WidgetFamily,
        size: CGSize? = nil,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let size = size ?? Self.size(of: family)
        assertAppearances(
            of: NextLessonEntryView(entry: entry, family: family),
            named: name, appearances: appearances, width: size.width, height: size.height,
            file: file, testName: testName, line: line
        )
    }

    private func assertDay(
        _ entry: ScheduleWidgetEntry,
        size: CGSize = WidgetSizes.large,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        assertAppearances(
            of: DayEntryView(entry: entry),
            named: name, appearances: appearances, width: size.width, height: size.height,
            file: file, testName: testName, line: line
        )
    }

    private func assertCards(
        _ entry: ScheduleWidgetEntry,
        family: WidgetFamily,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let size = Self.size(of: family)
        assertAppearances(
            of: CardsEntryView(entry: entry),
            named: name, appearances: appearances, width: size.width, height: size.height,
            file: file, testName: testName, line: line
        )
    }

    private func assertList(
        _ entry: ScheduleWidgetEntry,
        family: WidgetFamily,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let size = Self.size(of: family)
        assertAppearances(
            of: ClassicListEntryView(entry: entry, family: family),
            named: name, appearances: appearances, width: size.width, height: size.height,
            file: file, testName: testName, line: line
        )
    }

    private func assertSport(
        _ entry: SportWidgetEntry,
        family: WidgetFamily,
        named name: String,
        appearances: [SnapshotAppearance] = [.light, .dark],
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let size = Self.size(of: family)
        assertAppearances(
            of: SportEntryView(entry: entry, family: family),
            named: name, appearances: appearances, width: size.width, height: size.height,
            file: file, testName: testName, line: line
        )
    }

    private static func size(of family: WidgetFamily) -> CGSize {
        switch family {
        case .systemSmall: WidgetSizes.small
        case .systemLarge: WidgetSizes.large
        case .accessoryRectangular: WidgetSizes.accessoryRectangular
        case .accessoryInline: WidgetSizes.accessoryInline
        case .accessoryCircular: WidgetSizes.accessoryCircular
        default: WidgetSizes.medium
        }
    }

    private func assertQr(
        _ content: QrWidgetContent,
        appearance: ITMOWidgets.QrWidgetAppearance = .standard,
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
