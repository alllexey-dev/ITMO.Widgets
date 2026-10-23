import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Settings pages in all four appearances: the root and the deep pages exactly as the shared page providers
/// build them on iOS (the app's graph, iOS capabilities, fixed page states), and a page of every row type with long
/// values. The form scrolls, so each appearance gets a height that holds the whole page.
final class SettingsSnapshotTests: XCTestCase {
    func testRoot() {
        let state = SettingsFixtures.page(.root, SettingsFixtures.pageState(notificationsGranted: false))
        assertPage(state, named: "root", height: 1060, ax1Height: 1500)
    }

    func testSections() {
        let state = SettingsFixtures.page(.sections, SettingsFixtures.pageState(notificationsGranted: true))
        assertPage(state, named: "sections", height: 1180, ax1Height: 1900)
    }

    /// The calendar rows of Расписание, with the reason the sync turned itself off.
    func testSectionsWithTheCalendarDeleted() {
        let state = SettingsFixtures.page(
            .sections,
            SettingsFixtures.pageState(
                notificationsGranted: true,
                calendar: CalendarSyncState(enabled: false, problem: .calendarMissing)
            )
        )
        assertPage(state, named: "sections-calendar", height: 1180, ax1Height: 1900)
    }

    func testAbout() {
        let state = SettingsFixtures.page(.about, SettingsFixtures.pageState(notificationsGranted: true))
        assertPage(state, named: "about", height: 760, ax1Height: 1500)
    }

    func testEveryRowTypeWithLongValues() {
        assertPage(SettingsFixtures.longValues, named: "long", height: 1500, ax1Height: 3600)
    }

    /// The plans' source and the licence texts the map's MapLibre Native and Noto Sans ask for.
    func testLicenses() {
        let state = SettingsFixtures.page(.licenses, SettingsFixtures.pageState(notificationsGranted: false))
        assertPage(state, named: "licenses", height: 1500, ax1Height: 2700)
    }

    func testLoading() {
        let state = SettingsUiState(page: .root, sections: [], loaded: false, gallery: nil)
        assertPage(state, named: "loading", height: 320, ax1Height: 320)
    }

    private func assertPage(
        _ state: SettingsUiState,
        named name: String,
        height: CGFloat,
        ax1Height: CGFloat,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = NavigationStack {
            SettingsForm(state: state, system: SettingsSystemState(backgroundRefresh: .denied))
                .itmoDetailHeader(title: state.page.title.resolved, subtitle: nil)
        }
        assertAppearances(
            of: view, named: name, appearances: [.light, .dark], height: height,
            file: file, testName: testName, line: line
        )
        assertAppearances(
            of: view, named: name, appearances: [.lightAX1, .darkAX1], height: ax1Height,
            file: file, testName: testName, line: line
        )
    }
}

/// Page states for the settings snapshots: the stored values of a fresh install, built in Swift because SKIE
/// bridges no default arguments; texts of the long page are synthetic.
enum SettingsFixtures {
    static func pageState(
        notificationsGranted: Bool,
        calendar: CalendarSyncState = CalendarSyncState(enabled: false, problem: nil),
        local: LocalSettings = SettingsFixtures.local,
        placedWidgets: [WidgetFormat: KotlinInt] = [:]
    ) -> SettingsPageState {
        SettingsPageState(
            local: local,
            notificationsGranted: KotlinBoolean(bool: notificationsGranted),
            hasCustomSpoiler: false,
            imageBusy: false,
            calendar: calendar,
            diagnosticsCount: 0,
            bars: .notConnected,
            placedWidgets: placedWidgets
        )
    }

    /// The page as its provider builds it in the app's graph, with iOS's capabilities.
    static func page(_ page: SettingsPage, _ state: SettingsPageState) -> SettingsUiState {
        guard let pages = IosKoin.shared.get(type: SettingsPages.self) as? SettingsPages else {
            preconditionFailure("Koin resolved no SettingsPages")
        }
        let provider = pages.forPage(page: page)
        return SettingsUiState(
            page: page,
            sections: provider.sections(page: page, state: state),
            loaded: true,
            gallery: provider.gallery(page: page, state: state)
        )
    }

    static let local = localSettings()

    /// A fresh install's values; `barsMarksEnabled` is set once the account's first BARS answer came.
    static func localSettings(
        barsMarksEnabled: KotlinBoolean? = nil, theme: ThemeSpec? = nil, hidePastLessons: Bool = false
    ) -> LocalSettings {
        LocalSettings(
            customServicesEnabled: true,
            scheduleWidget: ScheduleWidgetSettings(
                compact: CompactScheduleWidgetSettings(
                    showNextLessonEarly: true,
                    hideTeacher: false,
                    textSize: .normal
                ),
                full: FullScheduleWidgetSettings(
                    hideTeacher: false,
                    hidePastLessons: hidePastLessons,
                    showTomorrowWhenTodayIsOver: false,
                    textSize: .normal
                )
            ),
            qrWidget: QrWidgetSettings(dynamicColors: true, spoilerEnabled: true, animationType: .circle),
            sport: SportDisplaySettings(hideTeacherSelector: true, hideTimeSelector: true),
            showSportAutoSign: false,
            scheduleChangesEnabled: true,
            myItmoMarksEnabled: true,
            barsMarksEnabled: barsMarksEnabled,
            sheetMarksEnabled: true,
            backgroundWorkHintShown: false,
            qrTileAdded: false,
            theme: theme ?? ThemeSpec(
                accent: .wallpaper,
                customArgb: ThemeSpec.companion.DEFAULT_CUSTOM_ARGB,
                style: .tonalSpot,
                contrast: .standard,
                pureBlack: false
            ),
            widgetsFollowTheme: true,
            homePassVisible: true,
            disabledPushes: []
        )
    }

    /// Every row type, enabled and disabled, with the longest realistic texts.
    static var longValues: SettingsUiState {
        let long = SnapshotFixtures.longSubjectName
        let person = SnapshotFixtures.longPersonName
        let items: [any SettingItem] = [
            SettingItemToggle(
                id: .homePassVisible, title: text(long), description: text(person), checked: true, enabled: true,
                icon: .qrCode
            ),
            SettingItemToggle(
                id: .scheduleSportAutoSign, title: text(long), description: nil, checked: false, enabled: false,
                icon: .sprint
            ),
            SettingItemChoice(
                id: .compactWidgetTextSize, title: text(long), value: text(person),
                options: [ChoiceOption(key: "LONG", label: text(person))],
                selectedOptionKey: "LONG", description: text(long), enabled: true, icon: nil
            ),
            SettingItemNavigation(
                id: .pageServices, title: text(long), value: text(person), description: nil,
                page: .services, enabled: true, icon: .cloudOff
            ),
            SettingItemAction(
                id: .diagnostics, title: text(long), description: text(person), value: text("200"),
                trailingIcon: .chevronRight, enabled: true, icon: .error
            ),
            SettingItemAction(
                id: .privacyPolicy, title: text(person), description: nil, value: nil,
                trailingIcon: .openInNew, enabled: false, icon: .lock
            ),
            SettingItemInfo(
                id: .licenseMaplibre, title: text(long), value: text("2.3.0-SNAPSHOT (2026-10-07, debug)"), icon: nil
            ),
            // The version row of a fork's build: a tap copies the details.
            SettingItemAction(
                id: .theVersion, title: text("Версия"), description: text("2.3.0 (20292) · a1b2c3d · AlexPer777/ITMO.Widgets"),
                value: nil, trailingIcon: nil, enabled: true, icon: .info
            ),
        ]
        return SettingsUiState(
            page: .about,
            sections: [SettingSection(title: text(long), items: items, footer: text(person))],
            loaded: true,
            gallery: nil
        )
    }

    private static func text(_ value: String) -> UiText {
        UiTextDynamic(value: value)
    }
}
