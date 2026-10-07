import Shared
import SwiftUI

/// The me tab's root: LA-3's Compose route (`meViewController`, IO-09e) with the router behind its rows. Its own
/// rows open friends, search, settings and the web sign-in; a key the demo refuses (the web sign-in) says
/// `error_demo_unavailable`, as Android's shell does. The sign-out asks in the route's own dialog. Named apart from
/// the exported Kotlin `MeScreen`.
struct MeTabScreen: View {
    let router: AppRouter
    @State private var messages = SocialMessages()

    var body: some View {
        ComposeHost { [router, messages] in
            meViewController(open: { route in messages.opened(router.open(route)) })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.me")
        .socialMessages(messages)
    }
}
