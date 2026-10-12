import CryptoKit
import Foundation
import LocalAuthentication

/// CryptoKit's HPKE for pairing (`PairingHost.hpkeSeal`): base mode, `.Curve25519_SHA256_ChachaPoly`, one `seal` with
/// an empty AAD, returned as the encapsulated key followed by the ciphertext, the layout Tink's `NO_PREFIX` uses.
enum PairingHpke {
    static func seal(recipientPublicKey: Data, info: Data, plaintext: Data) throws -> Data {
        let key = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: recipientPublicKey)
        var sender = try HPKE.Sender(recipientKey: key, ciphersuite: .Curve25519_SHA256_ChachaPoly, info: info)
        let ciphertext = try sender.seal(plaintext)
        return sender.encapsulatedKey + ciphertext
    }
}

/// The screen lock before an ITMO session leaves the phone (`PairingHost.unlockDevice`): Face ID, Touch ID or the
/// passcode.
enum DeviceUnlocker {
    static func canUnlock() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    static func unlock(reason: String, completion: @escaping (Bool) -> Void) {
        LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, _ in
            DispatchQueue.main.async { completion(success) }
        }
    }
}
