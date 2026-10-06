import Shared
import SwiftUI

/// The QR pass above home: LH-3's Compose route (`qrPassViewController`) with the screen at full brightness while it
/// is on screen and the app is active, so a turnstile reads the code (master P8; Android's pass keeps the user's
/// brightness).
struct QrPassScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var brightness = ScreenBrightness()
    @State private var isVisible = false

    var body: some View {
        ComposeHost { [dismiss] in
            qrPassViewController(onBack: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("qr.pass")
        .onAppear {
            isVisible = true
            if scenePhase == .active { brightness.raise() }
        }
        .onDisappear {
            isVisible = false
            brightness.restore()
        }
        .onChange(of: scenePhase) { _, phase in
            guard isVisible else { return }
            if phase == .active {
                brightness.raise()
            } else {
                brightness.restore()
            }
        }
    }
}

/// Full brightness for the pass and the user's own level back afterwards. The level is saved once per raise, so a
/// second raise never saves 1.0 as the user's level.
@MainActor
final class ScreenBrightness {
    private var saved: CGFloat?

    func raise() {
        guard saved == nil, let screen = Self.activeScreen() else { return }
        saved = screen.brightness
        screen.brightness = 1.0
    }

    func restore() {
        guard let level = saved else { return }
        saved = nil
        Self.activeScreen()?.brightness = level
    }

    /// The screen of the foreground scene; `UIScreen.main` is deprecated.
    private static func activeScreen() -> UIScreen? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return (scenes.first { $0.activationState == .foregroundActive } ?? scenes.first)?.screen
    }
}
