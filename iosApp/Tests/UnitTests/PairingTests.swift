import CryptoKit
@testable import ITMOWidgets
import Shared
import XCTest

/// Pairing's CryptoKit HPKE against RFC 9180 and Tink: the device key is RFC 9180 A.2's recipient, the bundle the
/// fixed synthetic one of `PairingVectors.kt` (`shared/feature-account/src/commonTest`), never a real token.
final class PairingTests: XCTestCase {
    private let privateKey = hex("8057991eef8f1f1af18f4a9491d16a1ce333f695d4db8e38da75975c4478e0fb")
    private let publicKey = hex("4310ee97d88cc1f088a5576c77ab0cf5c3ac797f3d95139c6c84b5429c59662a")
    private let info = hex(
        "69746d6f2d776964676574732070616972207631000190f1a23b4c7d5e8f60718293a4b5c6"
            + "4147454e5400000102030405060708090a0b0c0d0e0f"
    )
    private let bundle = #"{"challengeId":"0190f1a2-3b4c-7d5e-8f60-718293a4b5c6","kind":"AGENT","isu":100001,"#
        + #""refreshToken":"test-refresh-token","refreshExpiresAt":"2026-11-11T09:00:00Z","#
        + #""issuedAt":"2026-10-12T09:00:00Z","v":1}"#

    /// RFC 9180 A.2.1, sequence number 0: the suite is the one Tink and the agent use.
    func testTheSuiteOpensRfc9180A2() throws {
        let plaintext = try open(
            sealed: hex(
                "1afa08d3dec047a643885163f1180476fa7ddb54c6a8029ea33f95796bf2ac4a"
                    + "1c5250d8034ec2b784ba2cfd69dbdb8af406cfe3ff938e131f0def8c8b60b4db21993c62ce81883d2dd1b51a28"
            ),
            info: hex("4f6465206f6e2061204772656369616e2055726e"),
            aad: Data("Count-0".utf8)
        )
        XCTAssertEqual(String(decoding: plaintext, as: UTF8.self), "Beauty is truth, truth beauty")
    }

    func testTheSealedBundleOpensWithTheDeviceKey() throws {
        let sealed = try PairingHpke.seal(recipientPublicKey: publicKey, info: info, plaintext: Data(bundle.utf8))

        XCTAssertEqual(String(decoding: try open(sealed: sealed, info: info), as: UTF8.self), bundle)
        let again = try PairingHpke.seal(recipientPublicKey: publicKey, info: info, plaintext: Data(bundle.utf8))
        XCTAssertNotEqual(sealed, again)
    }

    /// What Tink sealed on Android (`TinkHpkeSender`); `TinkHpkeSenderTest` opens CryptoKit's the other way.
    func testABundleTinkSealedOpens() throws {
        let sealed = try XCTUnwrap(base64url(
            "NQaYRQiWkoh803FbVgae1Uc-d-uksrb2ZoGa6aLn7W2W8rEPk-tL5yt2H3nyXANI40JzBGbkMi4Kw2lWYA5biSfCLj"
                + "velCr5dQ72m4hCKj0q_5ZcvhAryWGLfqjduT9osCr0LfOI7shUcLR9SNkxpSA5tIR124in3Gijwnq2JrhQ6nZFr50"
                + "YVm7hdEi_ryRdbEjF5bts6TOT_VnqDDHeVMwtci-lHpZIvaKLkIJLjxbGBAvFqNMmoXB4TGAMIRr2lTGe4M9XiM5z"
                + "7kHWNnsRLdpss9en8ux0ygFADEgYF1-xCMqhlXoBIwDoBtUTxbQJYHMGQNoupng"
        ))

        XCTAssertEqual(String(decoding: try open(sealed: sealed, info: info), as: UTF8.self), bundle)
        XCTAssertThrowsError(try open(sealed: sealed, info: info.dropLast() + Data([0xff])))
    }

    func testAKeyCryptoKitRefusesIsNil() {
        let platform = AppPlatform()
        XCTAssertNil(platform.hpkeSeal(recipientPublicKey: Data(count: 31), info: info, plaintext: Data(bundle.utf8)))
    }

    private func open(sealed: Data, info: Data, aad: Data = Data()) throws -> Data {
        var recipient = try HPKE.Recipient(
            privateKey: Curve25519.KeyAgreement.PrivateKey(rawRepresentation: privateKey),
            ciphersuite: .Curve25519_SHA256_ChachaPoly,
            info: info,
            encapsulatedKey: sealed.prefix(32)
        )
        return try recipient.open(sealed.dropFirst(32), authenticating: aad)
    }

    private func base64url(_ text: String) -> Data? {
        let base64 = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        return Data(base64Encoded: base64 + String(repeating: "=", count: (4 - base64.count % 4) % 4))
    }
}

private func hex(_ text: String) -> Data {
    var data = Data()
    var index = text.startIndex
    while index < text.endIndex {
        let next = text.index(index, offsetBy: 2)
        data.append(UInt8(text[index..<next], radix: 16)!)
        index = next
    }
    return data
}
