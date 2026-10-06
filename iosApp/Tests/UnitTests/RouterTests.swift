@testable import ITMOWidgets
import XCTest

/// The shell's routing (IO-06b): `RouteQueue` keeps Android's `MainRouteQueue` semantics, `AppRouter` runs a route
/// once over its root, `AppRoute` parses `itmowidgets://route/<id>`.
@MainActor
final class RouterTests: XCTestCase {
    // MARK: RouteQueue

    func testQueuedRouteIsTakenOnce() {
        var queue = RouteQueue()
        queue.offer(.qrPass)

        XCTAssertEqual(queue.take(ready: true) { _ in true }, .qrPass)
        XCTAssertNil(queue.take(ready: true) { _ in true })
        XCTAssertNil(queue.pending)
    }

    func testNewerOfferReplacesWaitingRoute() {
        var queue = RouteQueue()
        queue.offer(.qrPass)
        XCTAssertNil(queue.take(ready: false) { _ in true })

        queue.offer(.today)

        XCTAssertEqual(queue.take(ready: true) { _ in true }, .today)
        XCTAssertNil(queue.take(ready: true) { _ in true })
    }

    func testRouteWaitsWhileSessionIsNotReady() {
        var queue = RouteQueue()
        queue.offer(.today)
        var selected: [ShellTab] = []

        XCTAssertNil(queue.take(ready: false) { selected.append($0); return true })

        XCTAssertEqual(selected, [], "a root is not selected before the session is ready")
        XCTAssertEqual(queue.pending, .today)
    }

    func testRouteWaitsWhileRootCannotBeSelected() {
        var queue = RouteQueue()
        queue.offer(.today)

        XCTAssertNil(queue.take(ready: true) { _ in false })
        XCTAssertEqual(queue.pending, .today)

        var selected: [ShellTab] = []
        XCTAssertEqual(queue.take(ready: true) { selected.append($0); return true }, .today)
        XCTAssertEqual(selected, [.schedule])
    }

    // MARK: AppRouter

    func testQrPassOpensOverHomeOnce() {
        let router = readyRouter()
        router.selectedTab = .sport

        router.open(.qrPass)

        XCTAssertEqual(router.selectedTab, .home)
        XCTAssertEqual(router.path(of: .home), [.qrPass])
        router.setPath([], of: .home)
        router.sessionChanged(ready: true)
        router.shellMounted(true)
        XCTAssertEqual(router.path(of: .home), [], "a route that ran never runs again")
    }

    func testTodayOpensScheduleRootOnToday() {
        let router = readyRouter()
        router.setPath([.qrPass], of: .schedule)

        router.open(id: "today")

        XCTAssertEqual(router.selectedTab, .schedule)
        XCTAssertEqual(router.path(of: .schedule), [])
        XCTAssertEqual(router.todayRequest, 1)
    }

    func testRouteWaitsForReadySessionThenRuns() {
        let router = AppRouter()
        router.shellMounted(true)
        router.open(.qrPass)

        XCTAssertEqual(router.pendingRoute, .qrPass)
        XCTAssertEqual(router.path(of: .home), [])

        router.sessionChanged(ready: true)

        XCTAssertNil(router.pendingRoute)
        XCTAssertEqual(router.path(of: .home), [.qrPass])
    }

    func testRouteWaitsUntilTabBarIsMounted() {
        let router = AppRouter()
        router.sessionChanged(ready: true)
        router.open(.today)

        XCTAssertEqual(router.pendingRoute, .today)
        XCTAssertEqual(router.selectedTab, .home)

        router.shellMounted(true)

        XCTAssertEqual(router.selectedTab, .schedule)
        XCTAssertNil(router.pendingRoute)
    }

    func testNewerRouteReplacesWaitingOneInRouter() {
        let router = AppRouter()
        router.open(.qrPass)
        router.open(.root(.sport))

        router.shellMounted(true)
        router.sessionChanged(ready: true)

        XCTAssertEqual(router.selectedTab, .sport)
        XCTAssertEqual(router.path(of: .home), [])
    }

    func testLeavingSessionResetsStacks() {
        let router = readyRouter()
        router.open(.qrPass)
        router.open(.linkUnavailable)

        router.sessionChanged(ready: false)

        XCTAssertEqual(router.path(of: .home), [])
        XCTAssertNil(router.sheet)
        XCTAssertEqual(router.selectedTab, .home)
    }

    func testUnknownLinkShowsSheetOverHome() throws {
        let router = readyRouter()
        router.selectedTab = .me

        XCTAssertTrue(router.open(url: try XCTUnwrap(URL(string: "itmowidgets://route/nowhere"))))

        XCTAssertEqual(router.selectedTab, .home)
        XCTAssertEqual(router.sheet, .linkUnavailable)
    }

    func testForeignUrlOpensNothing() throws {
        let router = readyRouter()

        XCTAssertFalse(router.open(url: try XCTUnwrap(URL(string: "https://example.com/route/qr_pass"))))
        XCTAssertNil(router.pendingRoute)
        XCTAssertNil(router.sheet)
    }

    // MARK: AppRoute and tabs

    func testUrlsParse() throws {
        let cases: [(String, AppRoute?)] = [
            ("itmowidgets://route/qr_pass", .qrPass),
            ("itmowidgets://route/today", .today),
            ("itmowidgets://route/home", .root(.home)),
            ("itmowidgets://route/schedule", .root(.schedule)),
            ("itmowidgets://route/sport", .root(.sport)),
            ("itmowidgets://route/me", .root(.me)),
            ("ITMOWIDGETS://ROUTE/qr_pass", .qrPass),
            ("itmowidgets://route/recordbook", .linkUnavailable),
            ("itmowidgets://route/", .linkUnavailable),
            ("itmowidgets://route/qr_pass/extra", .linkUnavailable),
            ("itmowidgets://other/qr_pass", nil),
            ("https://route/qr_pass", nil),
        ]
        for (string, expected) in cases {
            XCTAssertEqual(AppRoute(url: try XCTUnwrap(URL(string: string))), expected, string)
        }
    }

    func testRouteUrlsRoundTrip() throws {
        for route in AppRoute.routable {
            let url = try XCTUnwrap(route.url, "\(route)")
            XCTAssertEqual(AppRoute(url: url), route)
        }
    }

    func testTabsFollowAndroidOrderWithRecordbookHidden() {
        XCTAssertEqual(ShellTab.allCases, [.recordbook, .schedule, .home, .sport, .me])
        XCTAssertEqual(ShellTab.visible, [.schedule, .home, .sport, .me])
        XCTAssertEqual(ShellTab.launch, .home)
    }

    private func readyRouter() -> AppRouter {
        let router = AppRouter()
        router.sessionChanged(ready: true)
        router.shellMounted(true)
        return router
    }
}
