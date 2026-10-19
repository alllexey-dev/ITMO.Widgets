import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The campus map under the native bar on the shared `CampusMapSamples`, the GL view replaced by `MapPlaceholder`
/// (MapLibre draws nothing in a snapshot): the room, a near match, the route on a floor and the city step, picking
/// where a route goes, another building loading, search across buildings, a room missing from the plan, a building
/// without a plan and the error.
@MainActor
final class CampusMapSnapshotTests: XCTestCase {
    private let samples = CampusMapSamples.shared

    func testRoom() { assertMap(samples.Room, named: "room") }

    func testApproximate() { assertMap(samples.Approximate, named: "approximate", appearances: [.light, .dark]) }

    func testRoute() { assertMap(samples.Route, named: "route") }

    func testCity() { assertMap(samples.City, named: "city", appearances: [.light, .dark]) }

    func testPick() { assertMap(samples.Pick, named: "pick", appearances: [.light, .dark]) }

    func testSwitching() { assertMap(samples.Switching, named: "switching") }

    func testSearch() { assertMap(samples.Search, named: "search", appearances: [.light, .dark]) }

    func testMissing() { assertMap(samples.Missing, named: "missing", appearances: [.light, .dark]) }

    func testNoPlan() { assertMap(samples.NoPlan, named: "no-plan", appearances: [.light, .dark]) }

    func testError() { assertMap(samples.Failed, named: "error", appearances: [.light, .dark]) }

    private func assertMap(
        _ state: CampusMapUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            CampusMapContent(state: state) { _ in MapPlaceholder() }
                .campusMapChrome()
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
