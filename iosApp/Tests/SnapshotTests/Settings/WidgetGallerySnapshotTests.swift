import Shared
import SwiftUI
import WidgetKit
import XCTest
@testable import ITMOWidgets

/// The widgets gallery as the shared provider builds it on iOS (`SettingsPage.WIDGETS`, iOS capabilities), with its
/// live previews on the shared sample: two formats on the home screen, the others with how to add one, the Lock
/// Screen section and the theme switch; and the day format's sheet with the past lessons hidden. Drawn in a window,
/// which renders the glass buttons.
@MainActor
final class WidgetGallerySnapshotTests: XCTestCase {
    func testGallery() async throws {
        let state = SettingsFixtures.page(
            .widgets,
            SettingsFixtures.pageState(notificationsGranted: true, placedWidgets: [.next: KotlinInt(int: 1), .day: KotlinInt(int: 1)])
        )
        let gallery = try XCTUnwrap(state.gallery)
        let previews = await WidgetGalleryPreviews.load(gallery.cards)
        XCTAssertNotNil(previews.next)
        XCTAssertNotNil(previews.day)
        XCTAssertNotNil(previews.list)
        XCTAssertNotNil(previews.sport)
        XCTAssertNotNil(previews.pass)

        let view = NavigationStack {
            WidgetGalleryForm(gallery: gallery, sections: state.sections, previews: previews)
                .navigationTitle(Text(verbatim: state.page.title.resolved))
                .navigationSubtitle(Text(verbatim: gallery.subtitle?.resolved ?? ""))
        }
        assertScreenAppearances(of: view, named: "gallery", appearances: [.light, .dark], height: 2350)
        assertScreenAppearances(of: view, named: "gallery", appearances: [.lightAX1], height: 4000)
    }

    func testDaySheet() async throws {
        let state = SettingsFixtures.page(
            .widgets,
            SettingsFixtures.pageState(
                notificationsGranted: true, local: SettingsFixtures.localSettings(hidePastLessons: true)
            )
        )
        let gallery = try XCTUnwrap(state.gallery)
        let card = try XCTUnwrap(gallery.cards.first { $0.format == .day })
        let previews = await WidgetGalleryPreviews.load(gallery.cards)

        let view = ScrollView {
            WidgetSettingsSheetContent(card: card, previews: previews)
                .padding(.horizontal, ItmoKit.margin)
        }
        .background(Color(.systemGroupedBackground))
        assertScreenAppearances(of: view, named: "day-sheet", appearances: [.light, .dark], height: 900)
    }

    func testPassSheet() async throws {
        let state = SettingsFixtures.page(.widgets, SettingsFixtures.pageState(notificationsGranted: true))
        let gallery = try XCTUnwrap(state.gallery)
        let card = try XCTUnwrap(gallery.cards.first { $0.format == .pass })
        let previews = await WidgetGalleryPreviews.load(gallery.cards)

        let view = ScrollView {
            WidgetSettingsSheetContent(card: card, previews: previews)
                .padding(.horizontal, ItmoKit.margin)
        }
        .background(Color(.systemGroupedBackground))
        assertScreenAppearances(of: view, named: "pass-sheet", appearances: [.light, .dark], height: 560)
    }
}
