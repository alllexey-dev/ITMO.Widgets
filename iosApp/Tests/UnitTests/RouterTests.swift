@testable import ITMOWidgets
import Shared
import XCTest

/// The shell's routing on the shared route model (IO-06c): the shared `RouteQueue` runs an entry route once over its
/// tab, `ShellGate` gates it, `RouteURL` turns `itmowidgets://route/<id>` and app links into entry routes, and
/// `Routes` maps every shared key to an iOS screen, a sheet, a tab, the gate or "not on iOS".
@MainActor
final class RouterTests: XCTestCase {
    // MARK: RouteQueue

    func testQueuedRouteIsTakenOnce() {
        let queue = RouteQueue()
        queue.offer(route: qrPassEntry)

        XCTAssertEqual(take(queue, ready: true), qrPassEntry)
        XCTAssertNil(take(queue, ready: true))
        XCTAssertNil(queue.pending)
    }

    func testNewerOfferReplacesWaitingRoute() {
        let queue = RouteQueue()
        queue.offer(route: qrPassEntry)
        XCTAssertNil(take(queue, ready: false))

        queue.offer(route: todayEntry)

        XCTAssertEqual(take(queue, ready: true), todayEntry)
        XCTAssertNil(take(queue, ready: true))
    }

    func testRouteWaitsWhileSessionIsNotReady() {
        let queue = RouteQueue()
        queue.offer(route: todayEntry)
        var selected: [AppTab] = []

        XCTAssertNil(take(queue, ready: false) { selected.append($0); return true })

        XCTAssertEqual(selected, [], "a tab is not selected before the session is ready")
        XCTAssertEqual(queue.pending, todayEntry)
    }

    func testRouteWaitsWhileTabCannotBeSelected() {
        let queue = RouteQueue()
        queue.offer(route: todayEntry)

        XCTAssertNil(take(queue, ready: true) { _ in false })
        XCTAssertEqual(queue.pending, todayEntry)

        var selected: [AppTab] = []
        XCTAssertEqual(take(queue, ready: true) { selected.append($0); return true }, todayEntry)
        XCTAssertEqual(selected, [.schedule])
    }

    // MARK: Entry routes

    func testQrPassOpensOverHomeOnce() {
        let router = readyRouter()
        router.selectedTab = .sport

        XCTAssertTrue(router.open(id: "qr_pass"))

        XCTAssertEqual(router.selectedTab, .home)
        XCTAssertEqual(router.path(of: .home), [qrPass])
        XCTAssertEqual(router.path(of: .home).first?.chrome, .compose)
        router.setPath([], of: .home)
        router.sessionChanged(signedIn, onboarding: .passed)
        router.shellMounted(true)
        XCTAssertEqual(router.path(of: .home), [], "a route that ran never runs again")
    }

    func testTodayOpensScheduleRootOnToday() {
        let router = readyRouter()
        router.setPath([qrPass], of: .schedule)

        router.open(id: "today")

        XCTAssertEqual(router.selectedTab, .schedule)
        XCTAssertEqual(router.path(of: .schedule), [])
        XCTAssertEqual(router.todayRequest, 1)
        XCTAssertNil(router.consumeRequest(of: .schedule), "today is the schedule root's own signal")
    }

    func testRootOpensAsItIs() {
        let router = readyRouter()
        router.selectedTab = .sport
        router.open(id: "qr_pass")

        router.open(id: "sport")
        router.open(id: "home")

        XCTAssertEqual(router.selectedTab, .home)
        XCTAssertEqual(router.path(of: .home), [qrPass])
    }

    func testRouteWaitsForReadySessionThenRuns() {
        let router = AppRouter()
        router.shellMounted(true)
        router.open(id: "qr_pass")

        XCTAssertEqual(router.pendingRoute, qrPassEntry)
        XCTAssertEqual(router.path(of: .home), [])

        router.sessionChanged(signedIn, onboarding: .passed)

        XCTAssertNil(router.pendingRoute)
        XCTAssertEqual(router.path(of: .home), [qrPass])
    }

    func testRouteWaitsForFirstRunFlow() {
        let router = AppRouter()
        router.shellMounted(true)
        router.sessionChanged(signedIn, onboarding: .unknown)
        router.open(id: "today")
        router.sessionChanged(signedIn, onboarding: .required)

        XCTAssertEqual(router.pendingRoute, todayEntry)

        router.sessionChanged(signedIn, onboarding: .passed)

        XCTAssertEqual(router.selectedTab, .schedule)
        XCTAssertNil(router.pendingRoute)
    }

    func testRouteRunsInDemo() {
        let router = AppRouter()
        router.shellMounted(true)
        router.sessionChanged(ShellSessionState.demo.sessionState, onboarding: .unknown)

        router.open(id: "today")

        XCTAssertEqual(router.selectedTab, .schedule, "the demo session skips the first-run flow")
    }

    func testRouteWaitsUntilTabBarIsMounted() {
        let router = AppRouter()
        router.sessionChanged(signedIn, onboarding: .passed)
        router.open(id: "today")

        XCTAssertEqual(router.pendingRoute, todayEntry)
        XCTAssertEqual(router.selectedTab, .home)

        router.shellMounted(true)

        XCTAssertEqual(router.selectedTab, .schedule)
        XCTAssertNil(router.pendingRoute)
    }

    func testNewerRouteReplacesWaitingOneInRouter() {
        let router = AppRouter()
        router.open(id: "qr_pass")
        router.open(id: "sport")

        router.shellMounted(true)
        router.sessionChanged(signedIn, onboarding: .passed)

        XCTAssertEqual(router.selectedTab, .sport)
        XCTAssertEqual(router.path(of: .home), [])
    }

    func testLeavingSessionResetsStacks() throws {
        let router = readyRouter()
        router.open(id: "qr_pass")
        router.open(id: "nowhere")
        router.open(url: try XCTUnwrap(URL(string: "https://widgets.alllexey.dev/sport/42")))

        router.sessionChanged(ShellSessionState.signedOut.sessionState, onboarding: .passed)

        XCTAssertEqual(router.path(of: .home), [])
        XCTAssertNil(router.sheet)
        XCTAssertNil(router.consumeRequest(of: .sport))
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

        for string in ["https://example.com/route/qr_pass", "https://widgets.alllexey.dev/about", "mailto:a@b.c"] {
            XCTAssertFalse(router.open(url: try XCTUnwrap(URL(string: string))), string)
        }
        XCTAssertNil(router.pendingRoute)
        XCTAssertNil(router.sheet)
    }

    func testAppLinksFeedEntryRoutes() throws {
        let router = readyRouter()

        XCTAssertTrue(router.open(url: try XCTUnwrap(URL(string: "https://widgets.alllexey.dev/sport/42"))))
        XCTAssertEqual(router.selectedTab, .sport)
        XCTAssertEqual(
            router.consumeRequest(of: .sport) as? TabRequestSportLesson,
            TabRequestSportLesson(lessonId: 42, predicted: false)
        )
        XCTAssertNil(router.consumeRequest(of: .sport), "a request is handed out once")

        XCTAssertTrue(router.open(url: try XCTUnwrap(URL(string: "https://dev.widgets.alllexey.dev/u/100001"))))
        XCTAssertEqual(router.selectedTab, .me)
        XCTAssertEqual(router.path(of: .me), [ShellDestination(AppRoutes.UserProfile(isu: 100_001))])

        XCTAssertTrue(router.open(url: try XCTUnwrap(URL(string: "https://widgets.alllexey.dev/u/abc"))))
        XCTAssertEqual(router.selectedTab, .home)
        XCTAssertEqual(router.sheet, .linkUnavailable)
    }

    func testRecordbookEntryRouteSelectsTheRecordbookTab() throws {
        let router = readyRouter()
        let recordbook = IosRoutes.shared.entryRoute(action: AppEntryIntents.shared.ACTION_OPEN_RECORDBOOK)

        XCTAssertTrue(router.open(entry: try XCTUnwrap(recordbook)))
        XCTAssertEqual(router.selectedTab, .recordbook)
        XCTAssertNil(router.pendingRoute)
    }

    func testSubjectPageIsPushedAndItsSheetsOpenAboveTheShell() {
        let router = readyRouter()
        router.selectedTab = .recordbook
        let subject = AppRoutes.RecordbookSubject(args: RecordbookSubjectArgs(
            entryId: 11, programId: 1, semester: 3, studyYear: "2026/2027", barsPlan: nil, barsType: nil,
            barsIdentifier: nil
        ))
        let invalid = AppRoutes.RecordbookSubject(args: RecordbookSubjectArgs(
            entryId: 0, programId: 1, semester: 3, studyYear: "2026/2027", barsPlan: nil, barsType: nil,
            barsIdentifier: nil
        ))
        let scores = AppRoutes.SheetScores(args: SheetScoresArgs(
            subjectId: 5, subjectName: "Subject", periodKey: "2026/2027:1", url: "https://example.com/s", step: .total
        ))

        XCTAssertEqual(router.open(invalid), .notOnIOS, "a subject without valid arguments opens nothing")
        XCTAssertEqual(router.open(subject), .opened)
        XCTAssertEqual(router.open(scores), .opened)

        XCTAssertEqual(router.path(of: .recordbook), [ShellDestination(subject)])
        XCTAssertEqual(router.sheet, .route(ShellDestination(scores)))
    }

    // MARK: In-app routes

    func testScreenIsPushedOnSelectedTab() {
        let router = readyRouter()
        router.selectedTab = .sport
        router.sheet = .linkUnavailable

        XCTAssertEqual(router.open(AppRoutes.QrPass.shared), .opened)

        XCTAssertEqual(router.path(of: .sport), [qrPass])
        XCTAssertNil(router.sheet, "a screen closes the sheet it was opened from")
    }

    func testTabRootSelectsItsTab() {
        let router = readyRouter()

        XCTAssertEqual(router.open(AppRoutes.TabRoot(tab: .sport)), .opened)
        XCTAssertEqual(router.selectedTab, .sport)

        XCTAssertEqual(router.open(AppRoutes.TabRoot(tab: .recordbook)), .opened)
        XCTAssertEqual(router.selectedTab, .recordbook)
    }

    func testOneSheetAtATime() {
        let router = readyRouter()

        XCTAssertEqual(router.open(AppRoutes.LinkUnavailable.shared), .opened)
        XCTAssertEqual(router.open(AppRoutes.LinkUnavailable.shared), .ignored)
        XCTAssertEqual(router.sheet, .linkUnavailable)
    }

    /// A links sheet that leads on opens the next one in its place (IO-09f), where a plain open is ignored.
    func testReplaceSheetOpensTheNextSheetInThePlaceOfTheShownOne() {
        let router = readyRouter()
        let links = SubjectLinksArgs(subjectId: 5, subjectName: "Subject", periodKey: "2026/2027:1")
        let actions = AppRoutes.LinkActions(args: links, linkId: "link-1")
        let editor = AppRoutes.LinkEditor(args: links, linkId: "link-1")

        XCTAssertEqual(router.open(actions), .opened)
        XCTAssertEqual(router.open(editor), .ignored, "one sheet at a time")
        XCTAssertEqual(router.replaceSheet(with: editor), .opened)
        XCTAssertEqual(router.sheet, .route(ShellDestination(editor)))
    }

    func testNotOnIosRouteOpensNothing() {
        let router = readyRouter()
        let routes: [any AppRoute] = [
            AppRoutes.DebugTools.shared, AppRoutes.IcsExport.shared, AppRoutes.CancelBookingConfirm(lessonId: 42),
        ]

        for route in routes {
            XCTAssertEqual(router.open(route), .notOnIOS, "\(route)")
        }
        let export = AppRoutes.IcsExport.shared
        XCTAssertEqual(router.open(export) { (_: Int) in XCTFail("no screen, no callback") }, .notOnIOS)
        router.deliver(1, from: export)
        XCTAssertEqual(router.path(of: .home), [])
        XCTAssertNil(router.sheet)
    }

    /// A Compose sheet key (IO-09b): one sheet above the shell, whose answer reaches its opener once, and which a
    /// screen it opens replaces.
    func testRouteSheetOpensAboveTheShellAndAnswersItsOpener() {
        let router = readyRouter()
        router.selectedTab = .schedule
        let picker = AppRoutes.FriendSelector(selectedIsu: 100_001)
        var answers: [Int32?] = []

        XCTAssertEqual(router.open(picker) { (pick: FriendPick) in answers.append(pick.user?.isu) }, .opened)

        XCTAssertEqual(router.sheet, .route(ShellDestination(picker)))
        XCTAssertEqual(router.path(of: .schedule), [], "a sheet is not pushed")
        XCTAssertEqual(router.open(AppRoutes.FriendSelector(selectedIsu: 0)), .ignored, "one sheet at a time")
        router.deliver(FriendPick(user: nil), from: picker)
        router.deliver(FriendPick(user: nil), from: picker)
        XCTAssertEqual(answers, [nil])

        XCTAssertEqual(router.open(AppRoutes.UserProfile(isu: 100_001)), .opened)
        XCTAssertNil(router.sheet, "a profile opens in the sheet's place")
        XCTAssertEqual(router.path(of: .schedule), [ShellDestination(AppRoutes.UserProfile(isu: 100_001))])
    }

    func testGateRoutesNeverOpen() {
        let router = readyRouter()

        XCTAssertEqual(router.open(AppRoutes.Auth.shared), .ignored)
        XCTAssertEqual(router.open(AppRoutes.Onboarding.shared), .ignored)
    }

    func testShellGateDecidesBeforeTheMap() {
        let router = AppRouter()
        router.shellMounted(true)
        XCTAssertEqual(router.open(AppRoutes.QrPass.shared), .ignored, "nothing opens before the session is ready")

        router.sessionChanged(ShellSessionState.demo.sessionState, onboarding: .passed)
        XCTAssertEqual(router.open(AppRoutes.MyItmoWeb.shared), .refusedInDemo)
        XCTAssertEqual(router.open(AppRoutes.WebLogin(code: nil)), .refusedInDemo)

        router.sessionChanged(signedIn, onboarding: .passed)
        XCTAssertEqual(router.open(AppRoutes.MyItmoWeb.shared), .opened)
        XCTAssertEqual(router.open(AppRoutes.IcsExport.shared), .notOnIOS)
    }

    func testResultReturnsToItsOpenerOnce() {
        let router = readyRouter()
        var answers: [Int] = []

        XCTAssertEqual(router.open(AppRoutes.QrPass.shared) { (isu: Int) in answers.append(isu) }, .opened)
        router.deliver("not an ISU", from: AppRoutes.QrPass.shared)
        router.open(AppRoutes.QrPass.shared) { (isu: Int) in answers.append(isu * 10) }
        router.deliver(7, from: AppRoutes.QrPass.shared)
        router.deliver(8, from: AppRoutes.QrPass.shared)

        XCTAssertEqual(answers, [70], "a newer open replaces the callback; an answer runs it once")
    }

    func testLeavingSessionDropsResultCallbacks() {
        let router = readyRouter()
        var answered = false
        router.open(AppRoutes.QrPass.shared) { (_: Int) in answered = true }

        router.sessionChanged(ShellSessionState.loading.sessionState, onboarding: .passed)
        router.deliver(1, from: AppRoutes.QrPass.shared)

        XCTAssertFalse(answered)
    }

    // MARK: Route map

    func testEveryRegisteredRouteHasAFeature() {
        XCTAssertEqual(IosRoutes.shared.unmappedRoutes(), [])
    }

    func testEveryRouteKindHasItsTarget() {
        let names = IosRoutes.shared.registeredRouteNames()
        let sampled = Set(Self.expectedTargets.map { String(describing: type(of: $0.route as AnyObject)) })
        XCTAssertEqual(sampled.count, names.count, "one sample per registered key: \(names)")
        for (route, expected) in Self.expectedTargets {
            XCTAssertEqual(TargetKind(Routes.target(for: route)), expected, "\(route)")
        }
        for tab in ShellTab.allCases {
            let expected: TargetKind = tab.isAvailable ? .tab(tab) : .notOnIOS
            XCTAssertEqual(TargetKind(Routes.target(for: AppRoutes.TabRoot(tab: tab.appTab))), expected, "\(tab)")
        }
    }

    // MARK: URLs and tabs

    func testUrlsParse() throws {
        let cases: [(String, EntryRoute?)] = [
            ("itmowidgets://route/qr_pass", qrPassEntry),
            ("itmowidgets://route/today", todayEntry),
            ("itmowidgets://route/home", EntryRoute(tab: .home)),
            ("itmowidgets://route/schedule", EntryRoute(tab: .schedule)),
            ("itmowidgets://route/sport", EntryRoute(tab: .sport)),
            ("itmowidgets://route/me", EntryRoute(tab: .me)),
            ("ITMOWIDGETS://ROUTE/qr_pass", qrPassEntry),
            ("itmowidgets://route/recordbook", EntryRoute(tab: .recordbook)),
            ("itmowidgets://route/", linkUnavailableEntry),
            ("itmowidgets://route/qr_pass/extra", linkUnavailableEntry),
            ("itmowidgets://other/qr_pass", nil),
            ("https://route/qr_pass", nil),
        ]
        for (string, expected) in cases {
            XCTAssertEqual(RouteURL.entryRoute(url: try XCTUnwrap(URL(string: string))), expected, string)
        }
    }

    func testRouteUrlsRoundTrip() throws {
        for id in RouteURL.ids {
            let url = try XCTUnwrap(RouteURL.url(id: id), id)
            let route = try XCTUnwrap(RouteURL.entryRoute(url: url), id)
            XCTAssertEqual(route, RouteURL.entryRoute(id: id), id)
            XCTAssertNotEqual(route, linkUnavailableEntry, id)
        }
    }

    func testTabsFollowAndroidOrder() {
        XCTAssertEqual(ShellTab.allCases, [.recordbook, .schedule, .home, .sport, .me])
        XCTAssertEqual(ShellTab.visible, ShellTab.allCases, "every tab has its screen since IO-09d2")
        XCTAssertEqual(ShellTab.launch, .home)
        XCTAssertEqual(ShellTab.allCases.map(\.appTab), [.recordbook, .schedule, .home, .sport, .me])
        XCTAssertEqual(ShellTab.allCases.map { ShellTab($0.appTab) }, ShellTab.allCases)
    }

    // MARK: Fixtures

    private let signedIn = ShellSessionState.signedIn.sessionState
    private let qrPass = ShellDestination(AppRoutes.QrPass.shared)

    private let qrPassEntry = EntryRoute(
        tab: .home, overlay: AppRoutes.QrPass.shared, request: nil, activity: nil, alert: nil, shortcutId: "qr_pass"
    )

    private let todayEntry = EntryRoute(
        tab: .schedule, overlay: nil, request: TabRequestScheduleToday.shared, activity: nil, alert: nil,
        shortcutId: "today"
    )

    private let linkUnavailableEntry = EntryRoute(tab: .home, alert: AppRoutes.LinkUnavailable.shared)

    private func readyRouter() -> AppRouter {
        let router = AppRouter()
        router.sessionChanged(signedIn, onboarding: .passed)
        router.shellMounted(true)
        return router
    }

    private func take(
        _ queue: RouteQueue, ready: Bool, select: @escaping (AppTab) -> Bool = { _ in true }
    ) -> EntryRoute? {
        queue.take(ready: ready) { KotlinBoolean(bool: select($0)) }
    }

    /// `RouteTarget` without its view factories.
    private enum TargetKind: Equatable {
        case tab(ShellTab), gate, compose, swiftUI, sheet(ShellSheet), composeSheet, notOnIOS

        init(_ target: RouteTarget) {
            switch target {
            case let .tab(tab): self = .tab(tab)
            case .gate: self = .gate
            case .compose: self = .compose
            case .swiftUI: self = .swiftUI
            case let .sheet(sheet): self = .sheet(sheet)
            case .composeSheet: self = .composeSheet
            case .notOnIOS: self = .notOnIOS
            }
        }
    }

    /// One synthetic key of every registered class, in the order of `AppRoutes.registration`, and how iOS shows it
    /// today. A feature card that maps its keys changes its rows here.
    private static var expectedTargets: [(route: any AppRoute, target: TargetKind)] {
        let links = SubjectLinksArgs(subjectId: 5, subjectName: "Subject", periodKey: "2026/2027:1")
        let teacher = TeacherReviewArgs(teacherIsu: 100_002, teacherName: "Teacher")
        let subject = RecordbookSubjectArgs(
            entryId: 11, programId: 1, semester: 3, studyYear: "2026/2027", barsPlan: nil, barsType: nil,
            barsIdentifier: nil
        )
        let lesson = LessonDetailsArgs(
            pairId: 9, date: "2026-10-06", subjectName: "Subject", typeId: 1, format: "Format", start: "10:00",
            end: "11:30", teacherFio: nil, teacherIsu: nil, room: nil, building: nil, buildingId: nil,
            mainBuildingId: nil, note: nil, zoomUrl: nil, zoomPassword: nil, zoomInfo: nil, flowName: nil
        )
        let pendingSport = PendingSportDetailsArgs(
            lessonId: 42, sectionName: "Section", autoSign: true, isPrediction: false,
            start: "2026-10-06T10:00+03:00", end: "2026-10-06T11:30+03:00", teacherFio: "Teacher", roomName: "Room",
            teacherIsu: nil
        )
        let period = AppRoutes.RecordbookPeriod(
            programName: "Program", programNames: ["Program"], programIds: [KotlinLong(value: 1)],
            semesters: [KotlinInt(value: 1)], courses: [KotlinInt(value: 1)], years: ["2026/2027"],
            actual: [KotlinBoolean(value: true)], selectedProgram: 1, selectedSemester: 1
        )
        let scores = SheetScoresArgs(
            subjectId: 5, subjectName: "Subject", periodKey: "2026/2027:1", url: "https://example.com/s", step: .total
        )
        return [
            (AppRoutes.Auth.shared, .gate),
            (AppRoutes.Onboarding.shared, .gate),
            (AppRoutes.TabRoot(tab: .sport), .tab(.sport)),
            (AppRoutes.Settings(page: "ROOT"), .swiftUI),
            (AppRoutes.Diagnostics.shared, .swiftUI),
            (AppRoutes.DebugTools.shared, .notOnIOS),
            (AppRoutes.RecordbookSubject(args: subject), .compose),
            (AppRoutes.Friends.shared, .compose),
            (AppRoutes.UserFriends(isu: 100_001, name: ""), .compose),
            (AppRoutes.UserSearch.shared, .compose),
            (AppRoutes.UserProfile(isu: 100_001), .compose),
            (AppRoutes.UserSchedule(isu: 100_001, name: ""), .compose),
            (AppRoutes.UserSport(isu: 100_001, name: ""), .compose),
            (AppRoutes.ScheduleChanges.shared, .compose),
            (AppRoutes.QrPass.shared, .compose),
            (AppRoutes.MyItmoWeb.shared, .swiftUI),
            (period, .composeSheet),
            (AppRoutes.FriendSelector(selectedIsu: 0), .composeSheet),
            (AppRoutes.SheetScores(args: scores), .composeSheet),
            (AppRoutes.WebLogin(code: nil), .sheet(.webLogin(code: nil))),
            (AppRoutes.ReviewEditor(args: teacher), .composeSheet),
            (AppRoutes.SubjectLinks(args: links), .composeSheet),
            (AppRoutes.LinkEditor(args: links, linkId: nil), .composeSheet),
            (AppRoutes.IcsExport.shared, .notOnIOS),
            (AppRoutes.LinkActions(args: links, linkId: "link-1"), .composeSheet),
            (AppRoutes.LessonDetails(args: lesson), .composeSheet),
            (AppRoutes.PendingSportDetails(args: pendingSport), .composeSheet),
            // The reports are Compose dialogs inside the surface that asks for them (IO-09f).
            (AppRoutes.ReportReview(args: teacher, reviewId: "review-1"), .notOnIOS),
            (AppRoutes.ReportLink(args: links, linkId: "link-1"), .notOnIOS),
            (AppRoutes.LinkUnavailable.shared, .sheet(.linkUnavailable)),
            (AppRoutes.CancelBookingConfirm(lessonId: 42), .notOnIOS),
        ]
    }
}
