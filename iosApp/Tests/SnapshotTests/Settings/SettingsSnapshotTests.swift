import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Settings pages in all four appearances (IO-08a): the root and two deep pages exactly as the shared page providers
/// build them on iOS (the app's graph, iOS capabilities, fixed page states), and a page of every row type with long
/// values. The form scrolls, so each appearance gets a height that holds the whole page.
final class SettingsSnapshotTests: XCTestCase {
    func testRoot() {
        let state = SettingsFixtures.page(.root, SettingsFixtures.pageState(notificationsGranted: false))
        assertPage(state, named: "root", height: 900, ax1Height: 1250)
    }

    func testScheduleWithBackgroundRefreshOff() {
        let state = SettingsFixtures.page(
            .schedule,
            SettingsFixtures.pageState(notificationsGranted: false, backgroundWorkRestricted: true)
        )
        assertPage(state, named: "schedule", height: 640, ax1Height: 1400)
    }

    func testQrWidget() {
        let state = SettingsFixtures.page(.qrWidget, SettingsFixtures.pageState(notificationsGranted: true))
        assertPage(state, named: "qr", height: 420, ax1Height: 980)
    }

    func testEveryRowTypeWithLongValues() {
        assertPage(SettingsFixtures.longValues, named: "long", height: 1500, ax1Height: 3600)
    }

    func testLoading() {
        let state = SettingsUiState(page: .root, sections: [], loaded: false, previewSettings: nil)
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
                .navigationTitle(Text(verbatim: state.page.title.resolved))
                .navigationBarTitleDisplayMode(.inline)
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
        backgroundWorkRestricted: Bool = false
    ) -> SettingsPageState {
        SettingsPageState(
            local: local,
            sharing: SharingSettingsStateDisabled.shared,
            notificationsGranted: KotlinBoolean(bool: notificationsGranted),
            hasCustomSpoiler: false,
            imageBusy: false,
            backgroundWorkRestricted: backgroundWorkRestricted,
            calendar: CalendarSyncState(enabled: false, problem: nil),
            diagnosticsCount: 0
        )
    }

    /// The page as its provider builds it in the app's graph, with iOS's capabilities.
    static func page(_ page: SettingsPage, _ state: SettingsPageState) -> SettingsUiState {
        guard let pages = IosKoin.shared.get(type: SettingsPages.self) as? SettingsPages else {
            preconditionFailure("Koin resolved no SettingsPages")
        }
        let sections = pages.forPage(page: page).sections(page: page, state: state)
        return SettingsUiState(page: page, sections: sections, loaded: true, previewSettings: nil)
    }

    static let local = LocalSettings(
        customServicesEnabled: true,
        scheduleWidget: ScheduleWidgetSettings(
            compact: CompactScheduleWidgetSettings(showNextLessonEarly: true, hideTeacher: false, textSize: .normal),
            full: FullScheduleWidgetSettings(
                hideTeacher: false,
                hidePastLessons: false,
                showTomorrowWhenTodayIsOver: false,
                textSize: .normal
            )
        ),
        qrWidget: QrWidgetSettings(dynamicColors: true, spoilerEnabled: true, animationType: .circle),
        sport: SportDisplaySettings(hideTeacherSelector: true, hideTimeSelector: true),
        showSportAutoSign: false,
        scheduleChangesEnabled: true,
        myItmoMarksEnabled: true,
        barsMarksEnabled: nil,
        sheetMarksEnabled: true,
        hiddenHomeCards: [],
        backgroundWorkHintShown: false,
        qrTileAdded: false
    )

    /// Every row type, enabled and disabled, with the longest realistic texts.
    static var longValues: SettingsUiState {
        let long = SnapshotFixtures.longSubjectName
        let person = SnapshotFixtures.longPersonName
        let items: [any SettingItem] = [
            SettingItemToggle(
                id: .scheduleChanges, title: text(long), description: text(person),
                checked: true, enabled: true, stateKnown: true
            ),
            SettingItemToggle(
                id: .scheduleSportAutoSign, title: text(long), description: nil,
                checked: false, enabled: false, stateKnown: true
            ),
            SettingItemChoice(
                id: .compactWidgetTextSize, title: text(long), value: text(person),
                options: [ChoiceOption(key: "LONG", label: text(person))],
                selectedOptionKey: "LONG", description: text(long), enabled: true
            ),
            SettingItemNavigation(
                id: .pageServices, title: text(long), value: text(person), description: nil,
                page: .services, enabled: true
            ),
            SettingItemAction(
                id: .diagnostics, title: text(long), description: text(person), value: text("200"),
                trailingIcon: .chevronRight, enabled: true
            ),
            SettingItemAction(
                id: .refreshWidgets, title: text(person), description: nil, value: nil,
                trailingIcon: .refresh, enabled: false
            ),
            SettingItemInfo(id: .theVersion, title: text(long), value: text("2.3.0-SNAPSHOT (2026-10-07, debug)")),
        ]
        return SettingsUiState(
            page: .maintenance,
            sections: [SettingSection(title: text(long), items: items, footer: text(person))],
            loaded: true,
            previewSettings: nil
        )
    }

    private static func text(_ value: String) -> UiText {
        UiTextDynamic(value: value)
    }
}
