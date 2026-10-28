import Foundation

/// The cards widget's page: `cards-widget-v1.json` in the App Group container, written by `TurnCardsPageIntent` in
/// the widget extension; the app never reads it, and sign-out removes it with the rest of the container. One file for
/// every placed cards widget: a `StaticConfiguration` widget has no instance id, so the arrows turn all of them.
struct CardsWidgetPage: Equatable, Codable {
    /// The index into `ScheduleWidgetSnapshot.cardsPages`.
    let page: Int
    /// Past this instant the widget shows its first page again, Android's `CardsWidgetPage.RESET_AFTER` after the tap.
    let until: Date
    /// The first page's date (`yyyy-MM-dd`) at the tap: from a moment whose first page is another day (a midnight,
    /// today over) the widget shows its first page.
    let base: String

    /// How long a tap holds a page.
    static let duration: TimeInterval = 5 * 60

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("cards-widget", version: version)

    /// The snapshot version this build writes and the highest it reads.
    static let version = 1

    /// The page a tap at `now` turns to.
    static func startingAt(_ now: Date, page: Int, base: String) -> CardsWidgetPage {
        CardsWidgetPage(page: page, until: now.addingTimeInterval(duration), base: base)
    }

    /// The page in the App Group `container`; nil when there is none or it is unreadable (the first page shows).
    static func read(fromContainer container: URL?) -> CardsWidgetPage? {
        AppGroupSnapshot.read(CardsWidgetPage.self, file: fileName, maxVersion: version, in: container)
    }

    /// Replaces the page in `container`.
    func write(toContainer container: URL) throws {
        try AppGroupSnapshot.write(self, file: Self.fileName, version: Self.version, in: container)
    }

    /// The page shown at `date` by a snapshot whose pages start on `first`.
    func page(at date: Date, first: String?) -> Int {
        date < until && first == base ? page : 0
    }
}
