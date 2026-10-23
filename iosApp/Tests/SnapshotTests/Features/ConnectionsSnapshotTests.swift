import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Connected clients at the UX spec's states (vibe/plans/connected-clients-ux.md, iOS shots): the devices screen with
/// every section and without the server, an install's facts sheet, an agent with an ending sign-in, a gone client,
/// the renewal sheet and the pairing sheet's steps. The content views get synthetic states built from the catalog
/// keys, under the pushed header or in their sheet, in a window.
@MainActor
final class ConnectionsSnapshotTests: XCTestCase {
    // MARK: Devices

    func testDevices() {
        assertPushed(DevicesContent(state: devices()), title: "devices_title", named: "devices", height: 1250)
    }

    func testDevicesWithoutTheServer() {
        let state = devices(backend: false, others: OtherLoginsLoading.shared)
        assertPushed(
            DevicesContent(state: state), title: "devices_title", named: "devices-off", appearances: [.light, .dark]
        )
    }

    func testDevicesWithoutTheCookie() {
        let state = devices(others: OtherLoginsNoCookie.shared)
        assertPushed(
            DevicesContent(state: state), title: "devices_title", named: "devices-no-cookie", appearances: [.light],
            height: 1200
        )
    }

    func testInstallSheet() {
        let sheet = FactsSheet(
            key: "install:1",
            title: dynamic("Google Pixel 8"),
            subtitle: res("devices_install_app", ["2.4.0"]),
            facts: [
                Fact(title: res("devices_fact_sign_in"), value: dynamic("1 октября")),
                Fact(title: res("devices_fact_until"), value: dynamic("30 декабря")),
                Fact(title: res("devices_fact_activity"), value: res("devices_activity_yesterday", ["21:40"])),
                Fact(title: res("devices_fact_notifications"), value: res("devices_notifications_on")),
                Fact(title: res("devices_fact_ip"), value: dynamic("192.0.2.7")),
            ],
            action: res("devices_sign_out_action"),
            note: nil
        )
        let screen = NavigationStack {
            DevicesContent(state: devices())
                .itmoDetailHeader(title: AppStrings.string("devices_title"), subtitle: nil)
        }
        .sheet(isPresented: .constant(true)) { DevicesFactsSheet(sheet: sheet) }
        assertScreenAppearances(of: screen, named: "install-sheet", appearances: [.light, .dark], settle: 1.5)
    }

    // MARK: A client

    func testAgentWithAnEndingSignIn() {
        assertPushed(
            ConnectedClientContent(state: agent()),
            title: nil,
            named: "agent",
            height: 1000,
            header: (AppStrings.string("devices_client_agent_product", ["1.2"]), "nas")
        )
    }

    func testGoneClient() {
        let state = ConnectedClientUiState(
            loading: false, gone: true, title: dynamic("nas"), subtitle: nil, icon: .agent, signIn: nil, scopes: [],
            facts: [], revoke: res("connection_revoke"), confirm: nil, renew: nil, busy: false, message: nil,
            closed: false
        )
        assertPushed(ConnectedClientContent(state: state), title: nil, named: "gone", appearances: [.light])
    }

    func testRenewSheet() {
        let renew = RenewSheet(
            title: res("connection_renew_title", ["nas"]),
            command: "itmo-widgets-agent renew",
            note: res("connection_renew_note", ["nas"])
        )
        let screen = NavigationStack {
            ConnectedClientContent(state: agent()).itmoDetailHeader(title: "nas", subtitle: nil)
        }
        .sheet(isPresented: .constant(true)) { ClientRenewSheet(renew: renew, name: "nas") }
        assertScreenAppearances(of: screen, named: "renew", appearances: [.light, .dark], settle: 1.5)
    }

    // MARK: Pairing

    func testPairingInputWithAnError() {
        let state = PairingUiStateInput(text: "https://example.com", error: res("pair_link_unknown"))
        assertPairing(state, text: "https://example.com", named: "pair-input")
    }

    func testPairingConfirm() {
        let state = PairingUiStateConfirm(
            icon: .agent,
            name: dynamic("nas"),
            subtitle: res("devices_client_agent_product", ["1.2"]),
            code: "482 913",
            scopes: scopes(),
            note: res("pair_note_client", ["nas", "nas"]),
            primary: res("pair_continue"),
            renewal: false,
            busy: false
        )
        assertPairing(state, named: "pair-confirm", height: 1000)
    }

    func testPairingDone() {
        let state = PairingUiStateResult(
            success: true,
            title: res("pair_done", ["nas"]),
            text: res("pair_done_text", ["20 января"]),
            action: res("pair_done_action"),
            next: .close
        )
        assertPairing(state, named: "pair-done", appearances: [.light, .dark])
    }

    func testPairingProgress() {
        assertPairing(PairingUiStateSending.shared, named: "pair-progress", appearances: [.light])
    }

    // MARK: Helpers

    private func assertPushed<Content: View>(
        _ content: Content,
        title: String?,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        height: CGFloat = SnapshotMatrix.screenHeight,
        header: (subtitle: String, title: String)? = nil,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            content.itmoDetailHeader(
                title: header?.title ?? title.map { AppStrings.string($0) } ?? "",
                subtitle: header?.subtitle
            )
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, height: height, settle: 1, testName: testName
        )
    }

    private func assertPairing(
        _ state: PairingUiState,
        text: String = "",
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        height: CGFloat = SnapshotMatrix.screenHeight,
        testName: String = #function
    ) {
        let sheet = ItmoSheet(title: AppStrings.string("pair_title")) {
            PairingContent(state: state, text: .constant(text))
        }
        assertScreenAppearances(
            of: sheet, named: name, appearances: appearances, height: height, settle: 1, testName: testName
        )
    }

    /// Every section: this phone, another install, three clients (an ending sign-in, a closed one, a browser), two
    /// other sessions and the label switch.
    private func devices(backend: Bool = true, others: OtherLogins? = nil) -> DevicesUiState {
        let phone = DeviceRow(
            key: "install:this",
            icon: .phone,
            title: dynamic("iPhone 17"),
            subtitle: lines(
                res("devices_install_app", ["2.4.0"]),
                res("devices_sign_in_until_line", ["10 января"])
            ),
            pill: nil,
            connectionId: nil
        )
        let install = DeviceRow(
            key: "install:pixel",
            icon: .phone,
            title: dynamic("Google Pixel 8"),
            subtitle: lines(
                res("devices_install_app", ["2.4.0"]),
                res("devices_activity_line", [res("devices_activity_yesterday", ["21:40"])])
            ),
            pill: nil,
            connectionId: nil
        )
        let clients = [
            client(
                "nas", .agent, "devices_client_agent",
                Pill(text: res("devices_sign_in_until", ["26 октября"]), error: false)
            ),
            client("MacBook Air", .mcp, "devices_client_mcp", Pill(text: res("devices_sign_in_closed"), error: true)),
            client("Chrome, macOS", .web, "devices_client_web", nil),
        ]
        let sessions = OtherLoginsContent(rows: [
            session("Chrome 141, Windows", .computer, "3 сентября"),
            session("Safari, iOS", .phone, "28 сентября"),
        ])
        return DevicesUiState(
            backend: backend,
            loading: false,
            error: nil,
            thisPhone: phone,
            installs: backend ? [install] : [],
            clients: backend ? clients : [],
            others: others ?? sessions,
            browserLike: true,
            sheet: nil,
            confirm: nil,
            busy: false,
            refreshing: false,
            message: nil
        )
    }

    private func client(_ name: String, _ icon: DeviceIcon, _ type: String, _ pill: Pill?) -> DeviceRow {
        DeviceRow(
            key: "client:\(name)",
            icon: icon,
            title: dynamic(name),
            subtitle: res("devices_client_line", [res(type), res("devices_activity_minutes", [5])]),
            pill: pill,
            connectionId: name
        )
    }

    private func session(_ label: String, _ icon: DeviceIcon, _ started: String) -> DeviceRow {
        DeviceRow(
            key: "session:\(label)",
            icon: icon,
            title: dynamic(label),
            subtitle: res("devices_session_line", [started, res("devices_activity_today", ["10:12"])]),
            pill: nil,
            connectionId: nil
        )
    }

    /// The agent «nas»: four days left, three scopes, its facts.
    private func agent() -> ConnectedClientUiState {
        ConnectedClientUiState(
            loading: false,
            gone: false,
            title: dynamic("nas"),
            subtitle: res("devices_client_agent_product", ["1.2"]),
            icon: .agent,
            signIn: SignInStatus(
                tone: .warning,
                title: res("connection_sign_in_until", ["26 октября"]),
                text: UiTextPlural(key: "connection_sign_in_ending", count: 4, arguments: [4, "nas"]),
                action: res("connection_renew")
            ),
            scopes: scopes(),
            facts: [
                Fact(title: res("connection_fact_connected"), value: dynamic("28 июля")),
                Fact(title: res("devices_fact_activity"), value: res("devices_activity_minutes", [5])),
                Fact(title: res("connection_fact_label"), value: dynamic("Chrome 141, Linux")),
                Fact(title: res("devices_fact_ip"), value: dynamic("192.0.2.17")),
            ],
            revoke: res("connection_revoke"),
            confirm: nil,
            renew: nil,
            busy: false,
            message: nil,
            closed: false
        )
    }

    /// Backend's texts of three scopes, as the UX spec lists them.
    private func scopes() -> [ScopeSwitch] {
        [
            ScopeSwitch(
                id: "sport",
                title: dynamic("Запись на физкультуру"),
                subtitle: dynamic("Записывается по очередям вместо телефона"),
                checked: true
            ),
            ScopeSwitch(
                id: "friends:read", title: dynamic("Друзья"), subtitle: dynamic("Видит список друзей"), checked: false
            ),
            ScopeSwitch(
                id: "schedule:write",
                title: dynamic("Расписание для друзей"),
                subtitle: dynamic("Отправляет ваше расписание, чтобы друзья видели его без телефона"),
                checked: true
            ),
        ]
    }

    private func res(_ key: String, _ arguments: [Any] = []) -> UiText {
        UiTextRes(key: key, arguments: arguments)
    }

    private func dynamic(_ value: String) -> UiText {
        UiTextDynamic(value: value)
    }

    private func lines(_ first: UiText, _ second: UiText) -> UiText {
        UiTextJoined(parts: [first, second], separator: "\n")
    }
}
