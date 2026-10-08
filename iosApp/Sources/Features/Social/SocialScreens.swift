import Shared
import SwiftUI
import UIKit

/// The viewer's friends and requests (`AppRoutes.Friends`): L13's Compose route (`friendsViewController`, IO-09e).
struct FriendsScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = SocialMessages()

    var body: some View {
        ComposeHost { [dismiss, router, messages] in
            friendsViewController(open: { route in messages.opened(router.open(route)) }, onBack: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("social.friends")
        .socialMessages(messages)
    }
}

/// People search (`AppRoutes.UserSearch`): the Compose route with Compose's own field on the system keyboard
/// (`userSearchViewController`, IO-09e).
struct UserSearchScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = SocialMessages()

    var body: some View {
        ComposeHost { [dismiss, router, messages] in
            userSearchViewController(open: { route in messages.opened(router.open(route)) }, onBack: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("social.search")
        .socialMessages(messages)
    }
}

/// The person profile (`AppRoutes.UserProfile`, also a `/u/<isu>` link and a friendship notification): the Compose
/// route (`userProfileViewController`, IO-09e). The ISU goes to the clipboard here, with `person_isu_copied`, since
/// iOS confirms no copy itself.
struct UserProfileScreen: View {
    let isu: Int32
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = SocialMessages()

    var body: some View {
        ComposeHost { [isu, dismiss, router, messages] in
            userProfileViewController(
                isu: isu,
                open: { route in messages.opened(router.open(route)) },
                copyIsu: { copied in messages.copy(isu: copied.int32Value) },
                onBack: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("social.profile")
        .socialMessages(messages)
    }
}

/// Another user's friends (`AppRoutes.UserFriends`): the Compose route (`userFriendsViewController`, IO-09e).
struct UserFriendsScreen: View {
    let isu: Int32
    let name: String
    @Environment(\.dismiss) private var dismiss
    @Environment(AppRouter.self) private var router
    @State private var messages = SocialMessages()

    var body: some View {
        ComposeHost { [isu, name, dismiss, router, messages] in
            userFriendsViewController(
                isu: isu,
                name: name,
                open: { route in messages.opened(router.open(route)) },
                onBack: { dismiss() }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("social.userFriends")
        .socialMessages(messages)
    }
}

/// What the social hosts say beside their Compose route: a key the demo refuses and a copied ISU, as Android's
/// toasts do.
@MainActor
@Observable
final class SocialMessages {
    var message: SettingsMessage?

    /// The answer of the router to a row or button of the route.
    func opened(_ opening: RouteOpening) {
        guard opening == .refusedInDemo else { return }
        show(AppStrings.string("error_demo_unavailable"))
    }

    func copy(isu: Int32) {
        UIPasteboard.general.string = String(isu)
        show(AppStrings.string("person_isu_copied"))
    }

    private func show(_ text: String) {
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
