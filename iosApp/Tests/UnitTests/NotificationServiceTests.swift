@testable import ITMOWidgets
import Shared
import UserNotifications
import XCTest

/// The notification service's push handling (IO-12a) on the Kotlin golden fixtures, read by path: Backend's FCM
/// envelopes (BK-16), MyITMO's sign-up answers and KM-11c's golden sign-up outcomes and push traces, which Android's
/// `SportSignPushBooker` produces. `simctl push` never runs an extension (SP-23), so the extension's sources are
/// compiled into the app and driven here. Every ISU, token and lesson is synthetic.
final class NotificationServiceTests: XCTestCase {
    private static let now = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
    private static let recipient = 100_001
    private static let placeFree = "Освободилось место на занятии"
    private static let backend = URL(string: "https://backend.test")!

    private let bundle = Bundle(for: NotificationServiceTests.self)
    private var transport: FakeTransport!
    private var tokens: FakeTokenItem!
    private var container: URL!

    override func setUpWithError() throws {
        transport = FakeTransport()
        tokens = FakeTokenItem(Self.tokenValue(access: "synthetic-access", expiresIn: 3600))
        container = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: container, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: container)
    }

    // MARK: Golden sign-up outcomes

    func testSignUpOutcomesMatchTheKotlinGolden() async throws {
        let refusal = try fixture("sign-in-error", "json")
        let answers: [String: FakeTransport.Answer] = [
            "signed in": .reply(200, try fixture("lessons", "json")),
            "rejected with reasons (137)": .reply(200, refusal),
            "no capacity": .reply(200, Self.noCapacity(" synthetic-context-more-than-20")),
            "no capacity without context": .reply(200, Self.noCapacity(String(repeating: "x", count: 20))),
            "HTTP 400 envelope": .reply(400, refusal),
            "HTTP 502": .reply(502, Data("<html>Bad Gateway</html>".utf8)),
            "missing result": .reply(200, Data(#"{"error_code":0,"error_message":null,"result":null}"#.utf8)),
            "network": .network
        ]
        let golden = try goldenSections("sign-outcomes")["outcomes"] ?? []
        XCTAssertEqual(golden.count, answers.count, "every golden case is replayed")

        for line in golden {
            let parts = line.components(separatedBy: ": ")
            let answer = try XCTUnwrap(answers[parts[0]], "no Swift case for \(parts[0])")
            transport.answers[Self.signUp] = answer
            let outcome = await calls().signIn(9001, token: "synthetic-access")
            XCTAssertEqual(outcome.rawValue, parts[1], parts[0])
        }
    }

    func testTheNoCapacitySignatureIsAndroids() {
        XCTAssertEqual(SportSignOutcome.noCapacityMessage, "нельзя записать студента: нет свободных мест на занятии")
    }

    // MARK: Golden pushes

    /// Signed in, rejected, no places and a network failure, for both queues: the same MyITMO and Backend calls as
    /// the golden trace and Android's notice text. The app refreshes bookings, queues and widgets itself.
    func testPushesReplayTheKotlinGolden() async throws {
        let golden = try goldenSections("push")
        for kind in ["free", "auto"] {
            for (name, answer) in [
                ("signed in", FakeTransport.Answer.reply(200, try fixture("lessons", "json"))),
                ("rejected", .reply(200, try fixture("sign-in-error", "json"))),
                ("no capacity", .reply(200, Self.noCapacity(" synthetic-context-more-than-20"))),
                ("network", .network)
            ] {
                let trace = try XCTUnwrap(golden["\(kind) \(name)"], "golden case \(kind) \(name)")
                transport = FakeTransport()
                transport.answers[Self.signUp] = answer
                let content = try push("SPORT_\(kind.uppercased())_SIGN_LESSONS_PAYLOAD")

                let shown = await handler().handle(content)

                let calls = trace.filter {
                    $0.hasPrefix(Self.signUp) || $0.hasPrefix("backend POST /api/sport/\(kind)-sign/")
                }
                XCTAssertEqual(transport.requests, calls, "\(kind) \(name)")
                let notices = trace.filter { $0.hasPrefix("success [") || $0.hasPrefix("failure [") }
                if let first = notices.first {
                    let text = first[first.index(after: first.firstIndex(of: "[")!)..<first.firstIndex(of: "]")!]
                    let title = first.hasPrefix("success") ? "Вы записаны на спорт" : "Не удалось записать на спорт"
                    XCTAssertEqual(shown.title, title, "\(kind) \(name)")
                    XCTAssertEqual(shown.body, String(text), "\(kind) \(name)")
                    XCTAssertEqual(shown.userInfo["data"] as? String, content.userInfo["data"] as? String)
                } else if name == "network" {
                    XCTAssertEqual(shown.title, Self.placeFree, "Backend's alert when MyITMO never answered")
                } else {
                    XCTAssertTrue(shown.title.isEmpty && shown.body.isEmpty, "a full lesson shows nothing, as Android")
                }
            }
        }
    }

    // MARK: Gates

    func testAFriendshipPushPassesThrough() async throws {
        let content = try push("FRIENDSHIP_EVENT_PAYLOAD")
        let shown = await handler().handle(content)
        XCTAssertTrue(shown === content)
        XCTAssertTrue(transport.requests.isEmpty)
    }

    func testAPushForAnotherAccountIsDropped() async throws {
        let other = SessionFile(isu: 100_002, demo: false, alertsAllowed: true, servicesEnabled: true)
        let shown = await handler(session: other)
            .handle(try push("SPORT_FREE_SIGN_LESSONS_PAYLOAD"))
        XCTAssertTrue(shown.title.isEmpty && shown.body.isEmpty)
        XCTAssertTrue(transport.requests.isEmpty)
    }

    func testTheDemoAndASignedOutDeviceDropEveryPush() async throws {
        let demo = SessionFile(isu: Self.recipient, demo: true, alertsAllowed: true, servicesEnabled: true)
        for session in [demo, nil] {
            for name in ["SPORT_AUTO_SIGN_LESSONS_PAYLOAD", "FRIENDSHIP_EVENT_PAYLOAD"] {
                let shown = await handler(session: session).handle(try push(name))
                XCTAssertTrue(shown.title.isEmpty && shown.body.isEmpty, name)
            }
        }
        XCTAssertTrue(transport.requests.isEmpty, "nothing reaches MyITMO or Backend")
    }

    func testWithoutTheServicesOptInNothingIsBooked() async throws {
        let session = SessionFile(isu: Self.recipient, demo: false, alertsAllowed: true, servicesEnabled: false)
        let shown = await handler(session: session).handle(try push("SPORT_FREE_SIGN_LESSONS_PAYLOAD"))
        XCTAssertTrue(shown.title.isEmpty && shown.body.isEmpty)
        XCTAssertTrue(transport.requests.isEmpty)
    }

    func testAnEndedLessonIsNotBooked() async throws {
        let later = Self.now.addingTimeInterval(7 * 86_400)
        let shown = await handler(now: later).handle(try push("SPORT_AUTO_SIGN_LESSONS_PAYLOAD"))
        XCTAssertTrue(shown.title.isEmpty && shown.body.isEmpty)
        XCTAssertTrue(transport.requests.isEmpty)
    }

    // MARK: Session

    func testAnExpiredTokenIsRefreshedUnderTheLockAndStored() async throws {
        tokens.value = Self.tokenValue(access: "expired-access", expiresIn: -60)
        transport.answers[Self.tokenRefresh] = .reply(200, Data("""
            {"access_token":"fresh-access","expires_in":300,"refresh_token":"fresh-refresh",\
            "refresh_expires_in":1800,"id_token":"fresh-id","session_state":"synthetic"}
            """.utf8))

        let token = try await access().validAccessToken()

        XCTAssertEqual(token, "fresh-access")
        XCTAssertEqual(transport.requests, [Self.tokenRefresh])
        let form = try XCTUnwrap(transport.bodies.first)
        let fields = [
            "refresh_token=synthetic-refresh", "scopes=openid%20profile", "client_id=student-personal-cabinet",
            "grant_type=refresh_token"
        ]
        for field in fields {
            XCTAssertTrue(form.components(separatedBy: "&").contains(field), field)
        }
        let stored = try XCTUnwrap(tokens.value.flatMap(MyItmoTokens.init(serialized:)))
        XCTAssertEqual(stored.refreshToken, "fresh-refresh")
        XCTAssertEqual(stored.accessExpiresAt, Self.now.addingTimeInterval(300))
        XCTAssertTrue(FileManager.default.fileExists(atPath: lockFile.path))

        _ = try await access().validAccessToken()
        XCTAssertEqual(transport.requests.count, 1, "the stored token is valid now")
    }

    /// The app holds the lock and refreshes; the extension waits, re-reads and uses the app's token.
    func testARefreshTheAppFinishedWhileWaitingIsReused() async throws {
        tokens.value = Self.tokenValue(access: "expired-access", expiresIn: -60)
        let appHolds = expectation(description: "the app holds the lock")
        let app = Task { [self] in
            try await FileLock(file: lockFile).withLock {
                appHolds.fulfill()
                try await Task.sleep(nanoseconds: 300_000_000)
                tokens.value = Self.tokenValue(access: "app-access", expiresIn: 3600)
            }
        }
        await fulfillment(of: [appHolds], timeout: 5)

        let token = try await access().validAccessToken()

        try await app.value
        XCTAssertEqual(token, "app-access")
        XCTAssertTrue(transport.requests.isEmpty, "no second refresh")
    }

    func testAFailedRefreshKeepsBackendsAlertAndTheSession() async throws {
        let expired = Self.tokenValue(access: "expired-access", expiresIn: -60)
        tokens.value = expired
        transport.answers[Self.tokenRefresh] = .reply(400, Data(#"{"error":"invalid_grant"}"#.utf8))

        let content = try push("SPORT_FREE_SIGN_LESSONS_PAYLOAD")
        let shown = await handler().handle(content)

        XCTAssertEqual(shown.title, Self.placeFree)
        XCTAssertEqual(tokens.value, expired, "only the app signs out")
        XCTAssertFalse(transport.requests.contains { $0.hasPrefix("my ") })
    }

    func testTheSessionValueIsKotlinsFiveLines() throws {
        // `KeychainTokenStorage`: base64url without padding, `~` for none, expiries in epoch milliseconds.
        let value = "YWNjZXNz\n1790000000000\ncmVmcmVzaA\n1790000600000\naWQ"
        let tokens = try XCTUnwrap(MyItmoTokens(serialized: value))
        XCTAssertEqual(tokens.accessToken, "access")
        XCTAssertEqual(tokens.refreshToken, "refresh")
        XCTAssertEqual(tokens.idToken, "id")
        XCTAssertEqual(tokens.accessExpiresAt, Date(timeIntervalSince1970: 1_790_000_000))
        XCTAssertEqual(tokens.serialized, value)

        XCTAssertNil(MyItmoTokens(serialized: "~\n0\ncmVmcmVzaA\n1790000600000\n~"), "refresh token only")
        XCTAssertNil(MyItmoTokens(serialized: "YWNjZXNz\n1790000000000"))
    }

    /// The Kotlin `KeychainSecureStore` and the extension's reader share one item in the same access group.
    func testTheKeychainItemIsTheKotlinStoresItem() throws {
        let store = IosSecureStore.shared.keychain()
        let name = "notification-service-tests-item"
        let item = KeychainTokenItem(account: name)
        defer { store.delete(name: name) }

        store.write(name: name, value: "synthetic-value")
        XCTAssertEqual(try item.read(), "synthetic-value")

        try item.write("synthetic-rotated")
        XCTAssertEqual(store.read(name: name), "synthetic-rotated")
    }

    // MARK: Time limit

    func testTheTimeLimitDeliversBackendsAlertOnce() async throws {
        transport.answers[Self.signUp] = .hang
        let service = NotificationService()
        service.makeHandler = { [self] in handler() }
        let content = try push("SPORT_FREE_SIGN_LESSONS_PAYLOAD")
        let delivered = expectation(description: "delivered")
        var shown: [UNNotificationContent] = []

        service.didReceive(UNNotificationRequest(identifier: "synthetic", content: content, trigger: nil)) {
            shown.append($0)
            delivered.fulfill()
        }
        for _ in 0..<200 where transport.requests.isEmpty {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        service.serviceExtensionTimeWillExpire()
        await fulfillment(of: [delivered], timeout: 5)
        try await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(shown.count, 1, "one delivery, whichever comes first")
        XCTAssertEqual(shown.first?.title, Self.placeFree)
    }

    // MARK: Fixtures

    private static let signUp = "my POST /api/sport/sign/schedule/lessons"
    private static let tokenRefresh = "itmoid POST /auth/realms/itmo/protocol/openid-connect/token"

    private static let signedIn = SessionFile(isu: recipient, demo: false, alertsAllowed: true, servicesEnabled: true)

    private var lockFile: URL { container.appendingPathComponent("locks/myitmo-refresh.lock") }

    private static func noCapacity(_ context: String) -> Data {
        let message = SportSignOutcome.noCapacityMessage + context
        return Data(#"{"error_code":2,"error_message":"\#(message)","result":null}"#.utf8)
    }

    private static func tokenValue(access: String, expiresIn: TimeInterval) -> String {
        MyItmoTokens(
            accessToken: access, accessExpiresAt: now.addingTimeInterval(expiresIn),
            refreshToken: "synthetic-refresh", refreshExpiresAt: now.addingTimeInterval(86_400),
            idToken: "synthetic-id"
        ).serialized
    }

    private func access(now: Date = NotificationServiceTests.now) -> MyItmoAccess {
        MyItmoAccess(item: tokens, lock: FileLock(file: lockFile), transport: transport, now: { now })
    }

    private func calls() -> SportPushCalls {
        SportPushCalls(transport: transport, backend: Self.backend, appVersion: "2.3.0 (1); ios; dev")
    }

    private func handler(
        session: SessionFile? = NotificationServiceTests.signedIn,
        now: Date = NotificationServiceTests.now
    ) -> PushHandler {
        PushHandler(
            session: { session },
            booker: SportPushBooker(access: access(now: now), calls: calls(), now: { now })
        )
    }

    /// Backend's alert for the envelope in `contract/fcm/<name>.json`: `data` as the JSON string FCM passes.
    private func push(_ name: String) throws -> UNMutableNotificationContent {
        let url = try XCTUnwrap(bundle.url(forResource: name, withExtension: "json", subdirectory: "fcm"))
        let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let data = try JSONSerialization.data(withJSONObject: try XCTUnwrap(fixture["data"]))
        let content = UNMutableNotificationContent()
        content.title = Self.placeFree
        content.body = "Плавание, 07.10 10:00"
        content.threadIdentifier = "sport"
        content.userInfo = [
            "data": String(decoding: data, as: UTF8.self),
            "recipient_isu": fixture["recipient_isu"] ?? ""
        ]
        return content
    }

    private func fixture(_ name: String, _ type: String) throws -> Data {
        try Data(contentsOf: XCTUnwrap(bundle.url(forResource: name, withExtension: type)))
    }

    /// A golden file of `SportGolden`: `# <case>` headings, each followed by its lines.
    private func goldenSections(_ name: String) throws -> [String: [String]] {
        let text = String(decoding: try fixture(name, "txt"), as: UTF8.self)
        var sections: [String: [String]] = [:]
        var current = ""
        for line in text.components(separatedBy: "\n") where !line.isEmpty {
            if line.hasPrefix("# ") {
                current = String(line.dropFirst(2))
            } else {
                sections[current, default: []].append(line)
            }
        }
        return sections
    }
}

/// Records each request as the golden traces write it (`my POST <path> <body>`, `backend POST <path>`) and answers
/// from `answers`; a request without an answer gets an empty 200.
private final class FakeTransport: PushTransport {
    enum Answer {
        case reply(Int, Data)
        case network
        case hang
    }

    var answers: [String: Answer] = [:]
    private(set) var requests: [String] = []
    private(set) var bodies: [String] = []

    func send(_ request: URLRequest) async throws -> (status: Int, body: Data) {
        let service = switch request.url?.host {
        case "my.itmo.ru": "my"
        case "id.itmo.ru": "itmoid"
        default: "backend"
        }
        let key = "\(service) \(request.httpMethod ?? "") \(request.url?.path ?? "")"
        let body = request.httpBody.map { String(decoding: $0, as: UTF8.self) }
        requests.append(service == "my" ? "\(key) \(body ?? "")" : key)
        bodies.append(body ?? "")
        switch answers[key] ?? .reply(200, Data("{}".utf8)) {
        case let .reply(status, data): return (status, data)
        case .network: throw URLError(.notConnectedToInternet)
        case .hang:
            try await Task.sleep(nanoseconds: 60_000_000_000)
            throw URLError(.timedOut)
        }
    }
}

private final class FakeTokenItem: TokenItem {
    var value: String?

    init(_ value: String?) {
        self.value = value
    }

    func read() throws -> String? { value }

    func write(_ value: String) throws { self.value = value }
}
