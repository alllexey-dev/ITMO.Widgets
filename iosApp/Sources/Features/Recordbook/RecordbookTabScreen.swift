import Shared
import SwiftUI
import UIKit

/// The recordbook tab's root: L12's Compose recordbook (`recordbookRootPage`, IO-09d2) in Android's tab order. A
/// subject opens its page through the router, the period button the period picker, whose choice comes back through
/// the router (`open(_:onResult:)`); the BARS sign-in is IO-09d1's `BarsLoginSheet`, after which the list loads
/// again, as after `BarsLoginActivity`.
struct RecordbookTabScreen: View {
    let router: AppRouter
    @State private var host = RecordbookHost()

    var body: some View {
        ComposeHost { [router, host] in
            host.makeRoot(router: router)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.recordbook")
        .recordbookBarsLogin(host)
        .scheduleMessages(host.messages)
    }
}

/// What a recordbook page keeps beside its Compose screen: the Kotlin page it answers, the BARS sign-in sheet's
/// presentation and the messages Android shows as snackbars.
@MainActor
@Observable
final class RecordbookHost {
    let messages = ScheduleMessages()
    var showsBarsLogin = false
    @ObservationIgnored private var page: RecordbookPage?

    func makeRoot(router: AppRouter) -> UIViewController {
        let messages = messages
        let page = recordbookRootPage(
            open: { route in messages.opened(router.open(route)) },
            pickPeriod: { [weak self] period in self?.pickPeriod(period, router: router) },
            barsLogin: { [weak self] in self?.showsBarsLogin = true }
        )
        self.page = page
        return page.controller
    }

    func makeSubject(
        args: RecordbookSubjectArgs,
        router: AppRouter,
        onBack: @escaping () -> Void
    ) -> UIViewController {
        let messages = messages
        let page = recordbookSubjectPage(
            args: args,
            open: { route in messages.opened(router.open(route)) },
            barsLogin: { [weak self] in self?.showsBarsLogin = true },
            linkFailed: { messages.say("link_open_failed") },
            onBack: onBack
        )
        self.page = page
        return page.controller
    }

    /// The BARS sign-in stored a session: the sheet closes and the page loads again.
    func barsSignedIn() {
        showsBarsLogin = false
        page?.barsSignedIn()
    }

    /// The picker of the shown periods; its choice comes back once.
    private func pickPeriod(_ period: AppRoutes.RecordbookPeriod, router: AppRouter) {
        let opening = router.open(period) { [weak self] (choice: RecordbookIosRequestSelectPeriod) in
            self?.page?.send(request: choice)
        }
        messages.opened(opening)
    }
}

extension View {
    /// IO-09d1's BARS sign-in over a recordbook page while `host` asks for it.
    func recordbookBarsLogin(_ host: RecordbookHost) -> some View {
        sheet(isPresented: Binding(get: { host.showsBarsLogin }, set: { host.showsBarsLogin = $0 })) {
            BarsLoginSheet { host.barsSignedIn() }
        }
    }
}
