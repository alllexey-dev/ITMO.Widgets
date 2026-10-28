import AppIntents
import Foundation

/// The cards widget's arrows (`Button(intent:)`): records `page` of the pages starting on `base` in the App Group
/// (`CardsWidgetPage`) and returns; WidgetKit then reloads the widget's timeline, which shows that day until the page
/// ends. It runs in the widget extension, without Kotlin and without the network. Hidden from Shortcuts and Spotlight.
struct TurnCardsPageIntent: AppIntent {
    static let title: LocalizedStringResource = "ios_widget_cards_turn"
    static let isDiscoverable = false

    @Parameter(title: "ios_widget_cards_turn")
    var page: Int

    @Parameter(title: "ios_widget_cards_turn")
    var base: String

    init() {}

    init(page: Int, base: String) {
        self.page = page
        self.base = base
    }

    func perform() async throws -> some IntentResult {
        // An unsigned build has no container: the widget then shows the signed-out state and has no pages.
        if let container = AppGroupSnapshot.container() {
            try CardsWidgetPage.startingAt(Date(), page: page, base: base).write(toContainer: container)
        }
        return .result()
    }
}
