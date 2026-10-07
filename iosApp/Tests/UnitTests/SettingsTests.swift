import Shared
import UIKit
import XCTest
@testable import ITMOWidgets

/// The settings wiring in the app's graph (IO-08a): the routes are SwiftUI screens, every page resolves its own
/// `SettingsViewModel` from its route's page name and loads, and the iOS copy replaces the shared texts that name
/// Android.
@MainActor
final class SettingsTests: XCTestCase {
    func testSettingsAndDiagnosticsAreSwiftUIScreensAndTheDebugToolsStayOnAndroid() {
        XCTAssertEqual(Routes.target(for: AppRoutes.Settings(page: "SCHEDULE")).chrome, .native)
        XCTAssertEqual(Routes.target(for: AppRoutes.Diagnostics.shared).chrome, .native)
        guard case .notOnIOS = Routes.target(for: AppRoutes.DebugTools.shared) else {
            return XCTFail("the debug tools have no iOS screen")
        }
    }

    func testEveryPageResolvesItsOwnViewModelAndLoadsStoredValues() async {
        for page in SettingsPage.allCases {
            let model = ObservableViewModel<SettingsViewModel, SettingsUiState>(state: \.uiState) { store in
                let parameters: [Any?] = settingsPageParameters(page: page.name)
                return store.resolve(type: SettingsViewModel.self, parameters: parameters) as! SettingsViewModel
            }
            XCTAssertEqual(model.state.page, page)
            for await state in model.viewModel.uiState where state.loaded {
                XCTAssertEqual(state.page, page)
                break
            }
        }
    }

    func testAnUnknownPageNameOpensTheRoot() {
        let model = ObservableViewModel<SettingsViewModel, SettingsUiState>(state: \.uiState) { store in
            let parameters: [Any?] = settingsPageParameters(page: "SCHEDULE_WIDGETS_OLD")
            return store.resolve(type: SettingsViewModel.self, parameters: parameters) as! SettingsViewModel
        }
        XCTAssertEqual(model.state.page, .root)
    }

    func testTheNotificationRowSaysIosWhereTheSharedValueNamesAndroid() async {
        let model = settingsModel(page: "ROOT")
        model.viewModel.onNotificationPermissionChanged(granted: true)
        let copy = SettingsIosCopy(system: SettingsSystemState(backgroundRefresh: .available))

        for await state in model.viewModel.uiState where state.loaded {
            let row = state.sections.flatMap(\.items).compactMap { $0 as? SettingItemAction }
                .first { $0.id == .notifications }
            guard let value = row?.value, (value as? UiTextRes)?.resource.key == "settings_notifications_allowed" else {
                continue
            }
            XCTAssertEqual(copy.text(value), AppStrings.string("ios_settings_notifications_allowed"))
            XCTAssertNotEqual(copy.text(value), value.resolved)
            XCTAssertEqual(copy.text(state.page.title), state.page.title.resolved, "other texts stay as built")
            break
        }
    }

    func testTheSiteLinkUsesTheBuildsOrigin() throws {
        let url = try XCTUnwrap(SettingsLinks.webPage("/privacy.html"))
        XCTAssertEqual(url.scheme, "https")
        XCTAssertEqual(url.path, "/privacy.html")
    }

    func testTheGraphRecordsCrashesInTheJournal() {
        XCTAssertFalse(
            IosCrashHook.shared.install(diagnostics: IosKoin.shared.get(type: IosAppDiagnostics.self) as! IosAppDiagnostics),
            "startKoinIos installed the hook"
        )
    }

    private func settingsModel(page: String) -> ObservableViewModel<SettingsViewModel, SettingsUiState> {
        ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = settingsPageParameters(page: page)
            return store.resolve(type: SettingsViewModel.self, parameters: parameters) as! SettingsViewModel
        }
    }
}
