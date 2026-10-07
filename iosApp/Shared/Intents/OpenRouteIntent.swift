import AppIntents

/// Opens the app on a route: the action of the QR Control (`QrControl`). An `OpenIntent` compiled into the app and
/// the widget extension, so the system runs `perform()` in the app after bringing it to the foreground; the extension
/// never routes. Like Android's tile it only opens the app: a Control cannot draw the code. Hidden from Shortcuts,
/// where `ITMOWidgetsShortcuts` offers the same screens.
struct OpenRouteIntent: OpenIntent {
    static let title: LocalizedStringResource = "ios_intent_open_route_title"
    static let isDiscoverable = false

    @Parameter(title: "ios_intent_route_parameter")
    var target: IntentRoute

    init() {}

    init(route: IntentRoute) {
        target = route
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        target.open()
        return .result()
    }
}
