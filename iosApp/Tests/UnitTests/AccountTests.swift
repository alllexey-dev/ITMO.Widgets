@testable import ITMOWidgets
import Shared
import XCTest

/// The web sign-in, My ITMO web and the update offer on the app's graph (IO-08b): the ViewModels resolve with their
/// Koin parameters, My ITMO keeps only official pages inside, and the offer never checks without an App Store ID.
@MainActor
final class AccountTests: XCTestCase {
    // MARK: Web sign-in

    func testWebLoginViewModelResolvesWithAnEmptyField() {
        let store = ScreenViewModelStore()
        defer { store.clear() }
        let model = store.resolve(type: WebLoginViewModel.self, parameters: WebLoginIosParameters.shared.viewModel())

        let state = (model as? WebLoginViewModel)?.uiState.value
        XCTAssertEqual((state as? WebLoginUiStateInput)?.code, "")
    }

    func testFixtureIsOffWithoutItsArgument() {
        XCTAssertFalse(WebLoginFixture.isOn)
        XCTAssertNil(WebLoginFixture.viewModel(store: ScreenViewModelStore()))
    }

    func testWebLoginOpensAsASheetWithItsLinkCode() {
        XCTAssertEqual(target(AppRoutes.WebLogin(code: nil)), .sheet(.webLogin(code: nil)))
        XCTAssertEqual(target(AppRoutes.WebLogin(code: "ABCD2345")), .sheet(.webLogin(code: "ABCD2345")))
        XCTAssertEqual(ShellSheet.webLogin(code: "ABCD2345").id, "webLogin")
    }

    // MARK: My ITMO web

    func testOfficialPagesLoadInside() {
        let pages = [
            "https://my.itmo.ru/", "https://my.itmo.ru/schedule", "https://id.itmo.ru/auth", "https://MY.ITMO.RU:443/",
        ]
        for page in pages {
            XCTAssertEqual(decision(page, mainFrame: true, gesture: false), .load, page)
            XCTAssertEqual(decision(page, mainFrame: false, gesture: false), .load, page)
        }
    }

    func testAnotherHttpsPageTheUserTappedOpensOutside() {
        let page = "https://itmo.ru/news"
        XCTAssertEqual(decision(page, mainFrame: true, gesture: true), .openExternally(URL(string: page)!))
    }

    func testRedirectsFramesAndOtherSchemesAreBlocked() {
        // A redirect (no gesture) never leaves; a blocked main frame shows the error, a frame does not.
        XCTAssertEqual(decision("https://itmo.ru/news", mainFrame: true, gesture: false), .block(showsError: true))
        XCTAssertEqual(decision("https://ads.example.com/", mainFrame: false, gesture: true), .block(showsError: false))
        for page in [
            "http://my.itmo.ru/", "https://user@my.itmo.ru/", "tel:+70000000000",
            "itms-apps://apps.apple.com/app/id1", "javascript:alert(1)",
        ] {
            XCTAssertEqual(decision(page, mainFrame: true, gesture: true), .block(showsError: true), page)
        }
        // Another port is not the official origin: a redirect there is blocked, a tap opens it outside.
        XCTAssertEqual(decision("https://my.itmo.ru:8443/", mainFrame: true, gesture: false), .block(showsError: true))
        XCTAssertEqual(MyItmoWebDecision.of(url: nil, isMainFrame: true, userGesture: true), .block(showsError: true))
    }

    func testMyItmoWebIsASwiftUIScreenUnderTheNativeBar() {
        XCTAssertEqual(target(AppRoutes.MyItmoWeb.shared), .swiftUI)
        XCTAssertEqual(Routes.target(for: AppRoutes.MyItmoWeb.shared).chrome, .native)
    }

    // MARK: Update offer

    func testThisBuildHasNoAppStoreIdSoItNeverOffers() {
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "AppStoreID") as? String, "")
        XCTAssertNil(AppUpdateOffer.storeURL(bundle: .main))
    }

    func testAnAppStoreIdIsItsAppStorePage() {
        XCTAssertEqual(
            AppUpdateOffer.storeURL(appStoreId: "1234567890"),
            URL(string: "https://apps.apple.com/app/id1234567890")
        )
        XCTAssertNil(AppUpdateOffer.storeURL(appStoreId: nil))
        XCTAssertNil(AppUpdateOffer.storeURL(appStoreId: "$(APP_STORE_ID)"))
    }

    func testWithoutAStorePageTheOfferNeverReachesTheGate() async {
        var gates = 0
        let offer = AppUpdateOffer(storeURL: nil) { _ in
            gates += 1
            preconditionFailure("the gate is never built without a store page")
        }

        offer.check()
        await offer.follow()

        XCTAssertEqual(gates, 0)
        XCTAssertNil(offer.pending)
    }

    func testUpdateViewModelShowsTheOfferItIsOpenedWith() {
        let update = AppUpdate(
            installed: AppVersionName(raw: "2.3.0"),
            latest: AppVersionName(raw: "2.4.0"),
            note: "",
            unsupported: true
        )
        let store = ScreenViewModelStore()
        defer { store.clear() }
        let model = store.resolve(
            type: AppUpdateViewModel.self,
            parameters: AppUpdateIosParameters.shared.viewModel(update: update)
        ) as? AppUpdateViewModel

        let state = model?.uiState.value
        XCTAssertEqual(state?.installed, "2.3.0")
        XCTAssertEqual(state?.latest, "2.4.0")
        XCTAssertEqual(state?.unsupported, true)
    }

    // MARK: Helpers

    private func decision(_ page: String, mainFrame: Bool, gesture: Bool) -> MyItmoWebDecision {
        MyItmoWebDecision.of(url: URL(string: page), isMainFrame: mainFrame, userGesture: gesture)
    }

    private func target(_ route: any AppRoute) -> TargetKind {
        TargetKind(Routes.target(for: route))
    }

    /// `RouteTarget` without its view factories.
    private enum TargetKind: Equatable {
        case tab(ShellTab), gate, compose, swiftUI, sheet(ShellSheet), notOnIOS

        init(_ target: RouteTarget) {
            switch target {
            case let .tab(tab): self = .tab(tab)
            case .gate: self = .gate
            case .compose: self = .compose
            case .swiftUI: self = .swiftUI
            case let .sheet(sheet): self = .sheet(sheet)
            case .notOnIOS: self = .notOnIOS
            }
        }
    }
}
