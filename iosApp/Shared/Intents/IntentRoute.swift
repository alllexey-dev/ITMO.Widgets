import AppIntents

/// The screens the system surfaces open: the Control, the App Shortcuts and the quick actions. The raw values are
/// stable route ids (`RouteURL`, Android's `res/xml/shortcuts.xml` ids); placed Controls and quick action types keep
/// them, so they never change (`StableIdentifiersTests`).
enum IntentRoute: String, AppEnum, CaseIterable {
    case qrPass = "qr_pass"
    case today

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "ios_intent_route_parameter")

    static let caseDisplayRepresentations: [IntentRoute: DisplayRepresentation] = [
        .qrPass: DisplayRepresentation(title: "shortcut_qr_short", image: .init(systemName: "qrcode")),
        .today: DisplayRepresentation(title: "shortcut_today_short", image: .init(systemName: "calendar")),
    ]

    /// Offers the route to the app's router (`RouteInbox`).
    @MainActor
    @discardableResult
    func open() -> Bool {
        RouteInbox.shared.offer(id: rawValue)
    }
}
