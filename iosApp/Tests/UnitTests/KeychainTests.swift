import Security
import Shared
import XCTest

/// The Kotlin Keychain `SecureStore` (L18 IO-04a) inside the app's entitlements: a Kotlin/Native test binary has
/// none (errSecMissingEntitlement), so these checks run hosted in the app.
final class KeychainTests: XCTestCase {
    private let service = "dev.alllexey.itmowidgets"
    private let itemName = "keychain-tests-item"
    private var store: SecureStore!

    override func setUp() {
        super.setUp()
        store = IosSecureStore.shared.keychain()
        store.delete(name: itemName)
    }

    override func tearDown() {
        store.delete(name: itemName)
        super.tearDown()
    }

    func testRoundTrip() {
        XCTAssertNil(store.read(name: itemName))

        store.write(name: itemName, value: "first value")
        XCTAssertEqual(store.read(name: itemName), "first value")

        store.write(name: itemName, value: "second, Grüße 漢字")
        XCTAssertEqual(store.read(name: itemName), "second, Grüße 漢字")
    }

    func testItemSitsInTheInfoPlistAccessGroupAndOpensAfterFirstUnlock() throws {
        let group = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "KeychainGroup") as? String)

        store.write(name: itemName, value: "synthetic")

        let attributes = try XCTUnwrap(itemAttributes(), "no Keychain item \(itemName)")
        XCTAssertEqual(attributes[kSecAttrAccessGroup as String] as? String, group)
        XCTAssertEqual(attributes[kSecAttrService as String] as? String, service)
        let accessible = attributes[kSecAttrAccessible as String] as? String
        XCTAssertEqual(accessible, kSecAttrAccessibleAfterFirstUnlock as String)
        XCTAssertNil(attributes[kSecValueData as String], "attributes only, never the value")
    }

    func testDelete() {
        store.write(name: itemName, value: "synthetic")

        store.delete(name: itemName)

        XCTAssertNil(store.read(name: itemName))
        XCTAssertNil(itemAttributes())
        store.delete(name: itemName)
    }

    private func itemAttributes() -> [String: Any]? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: itemName,
            kSecReturnAttributes as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? [String: Any]
    }
}
