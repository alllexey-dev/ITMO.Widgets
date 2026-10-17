import Shared
import SwiftUI
import UIKit

/// What the social screens say at the bottom, as Android's snackbars do: a key the demo refuses, a failed action, a
/// copied ISU. A new message replaces the shown one and is announced to VoiceOver.
@MainActor
@Observable
final class SocialMessages {
    var message: SettingsMessage?

    /// The answer of the router to a row or button: a key the demo refuses says `error_demo_unavailable`.
    func opened(_ opening: RouteOpening) {
        guard opening == .refusedInDemo else { return }
        show(AppStrings.string("error_demo_unavailable"))
    }

    /// The ISU to the clipboard with `person_isu_copied`, since iOS confirms no copy itself.
    func copy(isu: Int32) {
        UIPasteboard.general.string = String(isu)
        show(AppStrings.string("person_isu_copied"))
    }

    /// A failed friendship or review action: `friends_action_failed` with the reason.
    func failed(_ error: any AppError) {
        show(AppStrings.string("friends_action_failed", [AppErrorTextsKt.toUiText(error).resolved]))
    }

    func show(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}

extension View {
    /// The message of `messages` at the bottom for a few seconds.
    func socialMessages(_ messages: SocialMessages) -> some View {
        overlay(alignment: .bottom) {
            if let message = messages.message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: .seconds(3))
                        if messages.message?.id == message.id { messages.message = nil }
                    }
            }
        }
        .animation(.default, value: messages.message?.id)
    }
}

/// The share sheet for a profile link or the invitation (`SocialShares`, Koin).
@MainActor
enum SocialSharing {
    static func profile(name: String, isu: Int32) {
        guard let shares else { return }
        Task { _ = try? await shares.profile(name: name, isu: isu) }
    }

    static func invitation() {
        guard let shares else { return }
        Task { _ = try? await shares.invitation() }
    }

    /// A link in the system (Safari, or Telegram for its channel); false when nothing opened it.
    static func openLink(_ url: String) -> Bool {
        guard let actions = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions else { return false }
        return actions.openLink(url: url)
    }

    private static var shares: SocialShares? { IosKoin.shared.get(type: SocialShares.self) as? SocialShares }
}

/// A catalog text with an ISU as its `%1$lld`, without digit grouping: an ISU is a number to type, not a count, and
/// the Russian locale of `AppStrings` would print `284 731`.
func isuText(_ key: String, _ isu: Int32) -> String {
    String(format: AppStrings.string(key), locale: nil, Int64(isu))
}
