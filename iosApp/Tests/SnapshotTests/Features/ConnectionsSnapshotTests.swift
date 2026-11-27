import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Connected clients at the UX spec's states (iOS shots): the devices screen with
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

    func testMcpWithFullAccess() {
        assertPushed(
            ConnectedClientContent(state: mcp(fullItmoAccess: true)),
            title: nil,
            named: "mcp-full-access",
            appearances: [.light, .dark],
            height: 1000,
            header: (AppStrings.string("devices_client_mcp_product", ["0.5"]), "MacBook Air")
        )
    }

    func testMcpWithoutItmo() {
        assertPushed(
            ConnectedClientContent(state: mcp(fullItmoAccess: false, signedIn: false)),
            title: nil,
            named: "mcp-without-itmo",
            appearances: [.light],
            height: 1000,
            header: (AppStrings.string("devices_client_mcp_product", ["0.5"]), "MacBook Air")
        )
    }

    func testGoneClient() {
        let state = ConnectedClientUiState(
            loading: false, gone: true, title: dynamic("nas"), subtitle: nil, icon: .agent, signIn: nil,
            fullItmoAccess: false, takeBack: false, scopes: [],
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
            caution: true,
            scopes: scopes(),
            note: res("pair_note_client", ["nas", "nas"]),
            primary: res("pair_continue"),
            renewal: false,
            busy: false,
            grants: [],
            ask: nil,
            ready: true
        )
        assertPairing(state, named: "pair-confirm", height: 1000)
    }

    /// itmo-mcp's protocol-2 card: Widgets grants, the refresh grant with its purpose, `itmo:sso` off with the warning
    /// and an ITMO grant this version cannot mint.
    func testPairingGrants() {
        let state = PairingUiStateConfirm(
            icon: .mcp,
            name: dynamic("MacBook Air"),
            subtitle: res("devices_client_mcp_product", ["0.5"]),
            caution: true,
            scopes: [],
            note: res("pair_note_client", ["MacBook Air", "MacBook Air"]),
            primary: res("pair_continue"),
            renewal: false,
            busy: false,
            grants: [
                GrantGroup(title: res("pair_group_widgets"), grants: [
                    grant("widgets:reviews:read", dynamic("Отзывы о преподавателях"), dynamic("Читает отзывы и их сводки")),
                    grant("widgets:links:read", dynamic("Ссылки предметов"), dynamic("Читает ссылки и чаты предметов")),
                ]),
                GrantGroup(title: res("pair_group_itmo"), grants: [
                    grant(
                        "itmo:refresh:student-personal-cabinet", res("grant_refresh_title"), res("grant_refresh_text"),
                        purpose: res("grant_purpose", ["чтобы ИИ-ассистент видел ваше расписание, зачётку и заявки"])
                    ),
                    grant(
                        "itmo:sso", res("grant_sso_title"), res("grant_sso_text"),
                        purpose: res("grant_purpose", ["чтобы ИИ-ассистент видел баллы БАРС и входил в другие сервисы ИТМО"]),
                        warning: res("grant_sso_warning"), checked: false
                    ),
                    grant(
                        "itmo:refresh:dev-stand", res("grant_other_title"), nil,
                        note: res("grant_unavailable"), checked: false, enabled: false
                    ),
                ]),
            ],
            ask: nil,
            ready: true
        )
        assertPairing(state, named: "pair-grants", appearances: [.light, .dark, .lightAX1], height: 1700)
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
                action: res("connection_renew"),
                signedIn: true
            ),
            fullItmoAccess: false,
            takeBack: true,
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

    /// itmo-mcp «MacBook Air»: signed in until 20 January (with `itmo:sso` when [fullItmoAccess]), or without an ITMO
    /// sign-in when not [signedIn]; its Widgets scopes and facts.
    private func mcp(fullItmoAccess: Bool, signedIn: Bool = true) -> ConnectedClientUiState {
        let signIn = signedIn
            ? SignInStatus(
                tone: .ok, title: res("connection_sign_in_until", ["20 января"]), text: nil, action: nil, signedIn: true
            )
            : SignInStatus(
                tone: .ok,
                title: res("connection_sign_in_none"),
                text: res("connection_sign_in_none_text", ["MacBook Air"]),
                action: res("connection_sign_in_give"),
                signedIn: false
            )
        var facts = [
            Fact(title: res("connection_fact_connected"), value: dynamic("22 октября")),
            Fact(title: res("devices_fact_activity"), value: res("devices_activity_minutes", [2])),
        ]
        if signedIn {
            facts.append(Fact(title: res("connection_fact_label"), value: dynamic("Chrome 141, macOS")))
            facts.append(Fact(title: res("devices_fact_ip"), value: dynamic("192.0.2.40")))
        }
        return ConnectedClientUiState(
            loading: false,
            gone: false,
            title: dynamic("MacBook Air"),
            subtitle: res("devices_client_mcp_product", ["0.5"]),
            icon: .mcp,
            signIn: signIn,
            fullItmoAccess: fullItmoAccess,
            takeBack: signedIn,
            scopes: [
                ScopeSwitch(
                    id: "reviews:read",
                    title: dynamic("Отзывы о преподавателях"),
                    subtitle: dynamic("Читает отзывы и их сводки"),
                    checked: true
                ),
                ScopeSwitch(
                    id: "links:read",
                    title: dynamic("Ссылки предметов"),
                    subtitle: dynamic("Читает ссылки и чаты предметов"),
                    checked: true
                ),
                ScopeSwitch(
                    id: "sport:queue",
                    title: dynamic("Очереди на физкультуру"),
                    subtitle: dynamic("Может вставать в очереди и выходить из них"),
                    checked: false
                ),
            ],
            facts: facts,
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

    private func grant(
        _ id: String,
        _ title: UiText,
        _ description: UiText?,
        purpose: UiText? = nil,
        warning: UiText? = nil,
        note: UiText? = nil,
        checked: Bool = true,
        enabled: Bool = true
    ) -> GrantSwitch {
        GrantSwitch(
            id: id, title: title, description: description, purpose: purpose, warning: warning, note: note,
            checked: checked, enabled: enabled
        )
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
