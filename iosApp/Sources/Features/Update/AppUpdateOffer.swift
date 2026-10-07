import Foundation
import Observation
import Shared
import SwiftUI

/// The iOS update offer (IO-08b), Android's `ShellHost` update check: once per process, on the tabs of a real
/// session, the shared `AppUpdateGateViewModel` asks Backend for the iOS release (`updateIosModule`); a newer one opens
/// `AppUpdateScreen` above the shell. The only channel is the App Store page of `APP_STORE_ID` (`AppStoreID` in the
/// app's Info.plist): while the ID is empty (until T13) there is no page, and the offer never checks or shows.
@MainActor
@Observable
final class AppUpdateOffer {
    /// The offer on screen; nil when there is none.
    private(set) var pending: PendingAppUpdateOffer?
    /// The App Store page `app_update_action` opens; nil without an ID.
    let storeURL: URL?

    @ObservationIgnored private let makeGate: @MainActor (ScreenViewModelStore) -> AppUpdateGateViewModel
    @ObservationIgnored private let store = ScreenViewModelStore()
    @ObservationIgnored private var gate: AppUpdateGateViewModel?

    init(
        storeURL: URL? = AppUpdateOffer.storeURL(bundle: .main),
        makeGate: @escaping @MainActor (ScreenViewModelStore) -> AppUpdateGateViewModel = AppUpdateOffer.gateFromGraph
    ) {
        self.storeURL = storeURL
        self.makeGate = makeGate
    }

    /// The App Store page of the bundle's `AppStoreID`; nil while the build setting is empty.
    nonisolated static func storeURL(bundle: Bundle) -> URL? {
        storeURL(appStoreId: bundle.object(forInfoDictionaryKey: "AppStoreID") as? String)
    }

    nonisolated static func storeURL(appStoreId: String?) -> URL? {
        AppStoreListing.shared.url(appStoreId: appStoreId).flatMap(URL.init(string:))
    }

    /// Checks once per process (the gate ignores later calls); nothing without a store page.
    func check() {
        guard storeURL != nil else { return }
        resolvedGate().checkForUpdate()
    }

    /// Delivers the gate's offers until the calling task is cancelled; returns at once without a store page.
    func follow() async {
        guard storeURL != nil else { return }
        for await update in resolvedGate().events {
            pending = PendingAppUpdateOffer(update: update)
        }
    }

    /// The screen closed: `app_update_later`, the close button, a swipe down or a stored skip.
    func dismiss() {
        pending = nil
    }

    private func resolvedGate() -> AppUpdateGateViewModel {
        if let gate { return gate }
        let created = makeGate(store)
        gate = created
        return created
    }

    static func gateFromGraph(_ store: ScreenViewModelStore) -> AppUpdateGateViewModel {
        guard let gate = store.resolve(type: AppUpdateGateViewModel.self) as? AppUpdateGateViewModel else {
            preconditionFailure("Koin resolved no AppUpdateGateViewModel")
        }
        return gate
    }

    deinit {
        store.clear()
    }
}

/// An offer as a sheet item.
struct PendingAppUpdateOffer: Identifiable {
    let update: AppUpdate

    var id: String { update.latest.raw }
}

extension View {
    /// The update offer above this view: `offer` checks whenever `checks` turns true (the gate keeps it to once per
    /// process) and shows each offer as a sheet.
    func appUpdateOffer(_ offer: AppUpdateOffer, checks: Bool) -> some View {
        modifier(AppUpdateOfferModifier(offer: offer, checks: checks))
    }
}

private struct AppUpdateOfferModifier: ViewModifier {
    let offer: AppUpdateOffer
    let checks: Bool

    func body(content: Content) -> some View {
        content
            .task { await offer.follow() }
            .onChange(of: checks, initial: true) { _, checks in
                if checks { offer.check() }
            }
            .sheet(item: pending) { pending in
                if let storeURL = offer.storeURL {
                    AppUpdateScreen(update: pending.update, storeURL: storeURL, close: offer.dismiss)
                }
            }
    }

    private var pending: Binding<PendingAppUpdateOffer?> {
        Binding(get: { offer.pending }, set: { if $0 == nil { offer.dismiss() } })
    }
}
