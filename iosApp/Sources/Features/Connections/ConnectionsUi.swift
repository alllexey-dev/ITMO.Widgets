import Shared
import SwiftUI

// The pieces the devices screen, a client's screen and the pairing sheet share (Android's `ConnectionsUi.kt`).

extension DeviceIcon {
    var symbol: AppSymbol {
        switch self {
        case .phone: .smartphone
        case .computer: .computer
        case .agent: .dns
        case .mcp: .smartToy
        case .web: .globe
        }
    }

    /// Clients are primary, installs and sessions neutral (UX spec section 2).
    var tone: ItmoTone {
        switch self {
        case .agent, .mcp, .web: .primary
        case .phone, .computer: .neutral
        }
    }
}

extension Pill {
    /// A client's sign-in problem: ending soon is a warning, closed an error.
    var kit: ItmoPill {
        ItmoPill(text: text.resolved, tone: error ? .bad : .warn)
    }
}

/// Fact rows, title and value; nothing without facts.
struct ConnectionFacts: View {
    let facts: [Fact]

    var body: some View {
        if !facts.isEmpty {
            ItmoListGroup(inset: ItmoKit.padding) {
                ForEach(Array(facts.enumerated()), id: \.offset) { _, fact in
                    ItmoRow(title: fact.title.resolved, trailing: fact.value.resolved)
                }
            }
        }
    }
}

extension View {
    /// A shared `ConfirmAsk` as the system's confirmation dialog with a destructive button; `dismiss` also runs when
    /// the dialog closes by itself.
    func connectionConfirm(
        _ ask: ConfirmAsk?,
        confirm: @escaping () -> Void,
        dismiss: @escaping () -> Void
    ) -> some View {
        confirmationDialog(
            Text(verbatim: ask?.title.resolved ?? ""),
            isPresented: Binding(get: { ask != nil }, set: { if !$0 { dismiss() } }),
            titleVisibility: .visible,
            presenting: ask
        ) { ask in
            Button(role: .destructive, action: confirm) {
                Text(verbatim: ask.confirm.resolved)
            }
            .accessibilityIdentifier("connections.confirm")
            Button(role: .cancel, action: dismiss) {
                Text(verbatim: AppStrings.string("common_cancel"))
            }
        } message: { ask in
            Text(verbatim: ask.text.resolved)
        }
    }

    /// The ViewModel's one-shot `message` as the settings banner (Android's snackbar), announced to VoiceOver;
    /// `shown` clears it in the ViewModel.
    func connectionMessage(_ text: String?, shown: @escaping () -> Void) -> some View {
        modifier(ConnectionMessage(text: text, shown: shown))
    }
}

private struct ConnectionMessage: ViewModifier {
    let text: String?
    let shown: () -> Void
    @State private var message: SettingsMessage?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message {
                    SettingsMessageBanner(text: message.text)
                        .task(id: message.id) {
                            try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                            if self.message?.id == message.id { self.message = nil }
                        }
                }
            }
            .animation(.default, value: message?.id)
            .onChange(of: text, initial: true) { _, text in
                guard let text else { return }
                message = SettingsMessage(text: text)
                AccessibilityNotification.Announcement(text).post()
                shown()
            }
    }
}

/// The screen lock of the app's Koin graph (`IosDeviceUnlock`).
@MainActor
func appDeviceUnlock() -> DeviceUnlock {
    guard let unlock = IosKoin.shared.get(protocol: DeviceUnlock.self) as? DeviceUnlock else {
        preconditionFailure("Koin resolved no DeviceUnlock")
    }
    return unlock
}
