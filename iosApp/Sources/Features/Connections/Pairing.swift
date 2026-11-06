import CryptoKit
import Foundation
import LocalAuthentication
import UIKit

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
/// passcode. It answers once the app is active again: LocalAuthentication answers while its own UI still holds the app
/// inactive, and the ITMO.ID page the answer opens (`ClientSignInPage`) never loaded or took a touch when its
/// WKWebView came up in that moment.
enum DeviceUnlocker {
    static func canUnlock() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    static func unlock(reason: String, completion: @escaping (Bool) -> Void) {
        LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, _ in
            DispatchQueue.main.async { AppActivation.whenActive { completion(success) } }
        }
    }
}

/// Work that must wait until the app is active, the scene in front and taking touches.
enum AppActivation {
    /// Runs `action` now when the app is active, else once at its next `didBecomeActiveNotification` on `center`.
    @MainActor
    static func whenActive(
        isActive: Bool = UIApplication.shared.applicationState == .active,
        center: NotificationCenter = .default,
        _ action: @escaping () -> Void
    ) {
        guard !isActive else { return action() }
        var observer: (any NSObjectProtocol)?
        observer = center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            observer.map(center.removeObserver)
            observer = nil
            action()
        }
    }
}
