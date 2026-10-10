import Foundation
import Security

/// Why the notification service has no answer for a lesson: no usable session, or ITMO.ID or the lock failed. Each
/// leaves the session as it was; only the app signs out.
enum PushAccessError: Error {
    case signedOut
    case lockFailed
    case refreshFailed
}

/// The session the app's `KeychainTokenStorage` keeps in the `myitmo_tokens` Keychain item (docs/ios.md, Keychain):
/// five lines, each token base64url without padding (`~` for none), each expiry in epoch milliseconds.
struct MyItmoTokens: Equatable {
    let accessToken: String
    let accessExpiresAt: Date
    let refreshToken: String
    let refreshExpiresAt: Date
    let idToken: String

    /// Nil unless the value has five fields and all three tokens: a refresh-token-only state is not a session yet.
    init?(serialized value: String) {
        let fields = value.components(separatedBy: "\n")
        guard fields.count == 5,
              let accessExpiry = Int64(fields[1]), let refreshExpiry = Int64(fields[3]),
              let access = Self.decode(fields[0]), let refresh = Self.decode(fields[2]), let id = Self.decode(fields[4])
        else { return nil }
        self.init(
            accessToken: access, accessExpiresAt: Self.date(accessExpiry),
            refreshToken: refresh, refreshExpiresAt: Self.date(refreshExpiry),
            idToken: id
        )
    }

    init(accessToken: String, accessExpiresAt: Date, refreshToken: String, refreshExpiresAt: Date, idToken: String) {
        self.accessToken = accessToken
        self.accessExpiresAt = accessExpiresAt
        self.refreshToken = refreshToken
        self.refreshExpiresAt = refreshExpiresAt
        self.idToken = idToken
    }

    var serialized: String {
        [
            Self.encode(accessToken), String(Self.millis(accessExpiresAt)),
            Self.encode(refreshToken), String(Self.millis(refreshExpiresAt)),
            Self.encode(idToken)
        ].joined(separator: "\n")
    }

    private static func decode(_ field: String) -> String? {
        guard field != "~" else { return nil }
        var base64 = field.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        guard let data = Data(base64Encoded: base64), let text = String(data: data, encoding: .utf8),
              !text.trimmingCharacters(in: .whitespaces).isEmpty
        else { return nil }
        return text
    }

    private static func encode(_ token: String) -> String {
        Data(token.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func date(_ millis: Int64) -> Date { Date(timeIntervalSince1970: Double(millis) / 1000) }

    private static func millis(_ date: Date) -> Int64 {
        let millis = date.timeIntervalSince1970 * 1000
        return millis >= Double(Int64.max) ? Int64.max : Int64(millis.rounded())
    }
}

/// Where the session value lives; the Keychain in the extension, a fake in tests.
protocol TokenItem {
    func read() throws -> String?
    func write(_ value: String) throws
}

/// The `myitmo_tokens` generic password of the app's `KeychainSecureStore`: service `dev.alllexey.itmowidgets`,
/// access group `KeychainGroup` of this process's Info.plist, or the default group when the build has no
/// entitlement for it (errSecMissingEntitlement), as the Kotlin store falls back.
struct KeychainTokenItem: TokenItem {
    var account = "myitmo_tokens"
    var group = Bundle.main.object(forInfoDictionaryKey: "KeychainGroup") as? String

    func read() throws -> String? {
        var result: CFTypeRef?
        let status = withGroup { query in
            var query = query
            query[kSecReturnData as String] = true
            query[kSecMatchLimit as String] = kSecMatchLimitOne
            return SecItemCopyMatching(query as CFDictionary, &result)
        }
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw PushAccessError.refreshFailed }
        return String(data: data, encoding: .utf8)
    }

    /// Replaces the item the app wrote; the extension never creates a session.
    func write(_ value: String) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        let status = withGroup { SecItemUpdate($0 as CFDictionary, attributes as CFDictionary) }
        guard status == errSecSuccess else { throw PushAccessError.refreshFailed }
    }

    private func withGroup(_ call: ([String: Any]) -> OSStatus) -> OSStatus {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "dev.alllexey.itmowidgets",
            kSecAttrAccount as String: account
        ]
        guard let group, !group.isEmpty else { return call(query) }
        query[kSecAttrAccessGroup as String] = group
        let status = call(query)
        guard status == errSecMissingEntitlement else { return status }
        query.removeValue(forKey: kSecAttrAccessGroup as String)
        return call(query)
    }
}

/// `flock(2)` on `<App Group>/locks/<name>.lock`, the file the app's `FileCrossProcessLock` takes, so the app and the
/// notification service never refresh the same session at once. Polls without blocking a thread; held only around
/// the refresh (0xdead10cc).
struct FileLock {
    let file: URL

    func withLock<T>(_ body: () async throws -> T) async throws -> T {
        let directory = file.deletingLastPathComponent()
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let descriptor = open(file.path, O_RDWR | O_CREAT | O_CLOEXEC, 0o600)
        guard descriptor >= 0 else { throw PushAccessError.lockFailed }
        defer { close(descriptor) }
        var wait: UInt64 = 5_000_000
        while flock(descriptor, LOCK_EX | LOCK_NB) != 0 {
            guard errno == EWOULDBLOCK || errno == EINTR else { throw PushAccessError.lockFailed }
            try await Task.sleep(nanoseconds: wait)
            wait = min(wait * 2, 100_000_000)
        }
        defer { flock(descriptor, LOCK_UN) }
        return try await body()
    }
}

/// MyItmoApi 2.x's `TokenManager.validAccessToken` for the notification service: the stored access token while it
/// is valid for 30 s more, else one ITMO.ID refresh under the `myitmo-refresh` lock after a re-read, so the app and
/// the extension rotate a session once. Failures leave the stored session untouched.
struct MyItmoAccess {
    let item: TokenItem
    let lock: FileLock?
    let transport: PushTransport
    let now: () -> Date

    static let tokenEndpoint = URL(string: "https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/token")!
    private static let clientId = "student-personal-cabinet"
    private static let clockSkew: TimeInterval = 30
    private static let unreserved = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    )

    func validAccessToken() async throws -> String {
        let tokens = try session()
        if usable(tokens) { return tokens.accessToken }
        guard let lock else { throw PushAccessError.lockFailed }
        return try await lock.withLock {
            let current = try session()
            if usable(current) { return current.accessToken }
            guard current.refreshExpiresAt > now() else { throw PushAccessError.signedOut }
            let refreshed = try await refresh(current.refreshToken)
            try item.write(refreshed.serialized)
            return refreshed.accessToken
        }
    }

    private func session() throws -> MyItmoTokens {
        guard let value = try item.read(), let tokens = MyItmoTokens(serialized: value) else {
            throw PushAccessError.signedOut
        }
        return tokens
    }

    private func usable(_ tokens: MyItmoTokens) -> Bool {
        tokens.accessExpiresAt > now().addingTimeInterval(Self.clockSkew)
    }

    /// The 1.x wire form MyItmoApi keeps: `scopes` (plural), the MyITMO client, `grant_type=refresh_token`.
    private func refresh(_ refreshToken: String) async throws -> MyItmoTokens {
        let form = [
            ("refresh_token", refreshToken), ("scopes", "openid profile"),
            ("client_id", Self.clientId), ("grant_type", "refresh_token")
        ].map { "\($0)=\(Self.formEncoded($1))" }.joined(separator: "&")
        let request = URLRequest.push(
            "POST", Self.tokenEndpoint, body: Data(form.utf8), contentType: "application/x-www-form-urlencoded"
        )
        let issuedAt = now()
        let answer = try await transport.send(request)
        guard (200..<300).contains(answer.status),
              let wire = try? JSONDecoder().decode(TokenWire.self, from: answer.body),
              !wire.accessToken.isEmpty, !wire.refreshToken.isEmpty, !wire.idToken.isEmpty,
              wire.expiresIn >= 0, wire.refreshExpiresIn >= 0
        else { throw PushAccessError.refreshFailed }
        return MyItmoTokens(
            accessToken: wire.accessToken, accessExpiresAt: issuedAt.addingTimeInterval(wire.expiresIn),
            refreshToken: wire.refreshToken, refreshExpiresAt: issuedAt.addingTimeInterval(wire.refreshExpiresIn),
            idToken: wire.idToken
        )
    }

    private static func formEncoded(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
    }

    /// ITMO.ID's token answer; other fields (`session_state`) are ignored.
    private struct TokenWire: Decodable {
        let accessToken: String
        let expiresIn: TimeInterval
        let refreshToken: String
        let refreshExpiresIn: TimeInterval
        let idToken: String

        enum CodingKeys: String, CodingKey {
            case accessToken = "access_token"
            case expiresIn = "expires_in"
            case refreshToken = "refresh_token"
            case refreshExpiresIn = "refresh_expires_in"
            case idToken = "id_token"
        }
    }
}
