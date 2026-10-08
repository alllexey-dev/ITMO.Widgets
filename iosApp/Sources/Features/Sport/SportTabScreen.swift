import Shared
import SwiftUI

/// The sport tab's root: LP-6's Compose route with its my and sign pages (`sportViewController`, IO-09c). What
/// Android's `SportFragment` does beside the route stays here: a lesson the router hands the tab
/// (`TabRequest.SportLesson`, from a `/sport/<id>` link or a sport notification) goes to the sign page, and a booking
/// or lesson opens its details sheet as a SwiftUI sheet hosting the shared sheet content (recipe ios-cmp-host,
/// Sheets).
struct SportTabScreen: View {
    let router: AppRouter
    @State private var host = SportHost()

    var body: some View {
        ComposeHost { [host] in
            sportViewController(tab: host.tab, openDetails: { request in host.open(request) })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.root.sport")
        .onChange(of: router.hasRequest(of: .sport), initial: true) { _, waiting in
            if waiting, let request = router.consumeRequest(of: .sport) { host.receive(request) }
        }
        .sheet(item: Binding(get: { host.details }, set: { host.details = $0 })) { details in
            SportDetailsSheetView(tab: host.tab, details: details, router: router)
        }
    }
}

/// The state the sport root keeps beside the Compose route: the route's channels and the open details sheet.
@MainActor
@Observable
final class SportHost {
    let tab = SportTabState()
    var details: SportDetailsItem?

    func open(_ request: SportDetailsRequest) {
        details = SportDetailsItem(request: request)
    }

    /// A shared lesson for the sign page; the sport tab takes no other request.
    func receive(_ request: TabRequest) {
        guard let lesson = request as? TabRequestSportLesson else { return }
        details = nil
        tab.openSharedLesson(lessonId: lesson.lessonId, predicted: lesson.predicted)
    }
}

/// One presentation of the details sheet: a new identity for every opening, as each opening is a new sheet.
struct SportDetailsItem: Identifiable {
    let id = UUID()
    let request: SportDetailsRequest
}

/// The sport details sheet (`sportDetailsViewController`): Android's 90 % sheet is the large detent here, with the
/// system drag indicator over the shared content's own title row and close button.
struct SportDetailsSheetView: View {
    let tab: SportTabState
    let details: SportDetailsItem
    let router: AppRouter
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ComposeHost { [tab, details, router, dismiss] in
            sportDetailsViewController(
                tab: tab,
                request: details.request,
                open: { route in router.open(route) },
                onClose: { dismiss() }
            )
        }
        // The grouped background of a sheet, which the shared content paints too (iOS platform style).
        .presentationBackground(Color(uiColor: .systemGroupedBackground))
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sport.details")
    }
}

/// Another user's sport (`AppRoutes.UserSport`, from a profile): the Compose route (`userSportViewController`,
/// IO-09c), read-only.
struct UserSportScreen: View {
    let isu: Int32
    let name: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ComposeHost { [isu, name, dismiss] in
            userSportViewController(isu: isu, name: name, onBack: { dismiss() })
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sport.user")
    }
}
