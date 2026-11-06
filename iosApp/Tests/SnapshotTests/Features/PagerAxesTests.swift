import Shared
import SwiftUI
import UIKit
import XCTest
@testable import ITMOWidgets

/// The horizontal pagers of the schedule and sport roots move only sideways: none holds the pull to refresh, which
/// would let it bounce vertically and drag on both axes, while the vertical list under it keeps the pull. Read from
/// the scroll views UIKit lays out in a window, in the shell as the app shows the tabs.
@MainActor
final class PagerAxesTests: XCTestCase {
    func testTheScheduleWeekPagerMovesOnlySideways() {
        let shell = ShellTabs(selection: .constant(.schedule), openPass: {}) { _ in
            NavigationStack {
                ScheduleFeedContent(state: ScheduleSamples.shared.Feed)
                    .itmoRootHeader(title: ShellTab.schedule.title, subtitle: nil)
            }
        }
        assertPagersMoveOnlySideways(shell, sideways: 1)
    }

    func testTheSportPagersMoveOnlySideways() {
        let samples = SportSamples.shared
        for tab in [SportTab.my, .sign] {
            let shell = ShellTabs(selection: .constant(.sport), openPass: {}) { _ in
                NavigationStack {
                    SportContent(tab: .constant(tab), my: samples.my(state: "active"), sign: samples.sign(filters: "none", day: "normal"))
                        .itmoRootHeader(title: ShellTab.sport.title, subtitle: nil)
                }
            }
            // Sign-up adds its quick-filter chips and week strip, inside its page.
            assertPagersMoveOnlySideways(shell, sideways: tab == .my ? 1 : 3)
        }
    }

    private func assertPagersMoveOnlySideways<V: View>(
        _ view: V, sideways count: Int, file: StaticString = #filePath, line: UInt = #line
    ) {
        let scrolls = layOut(view)
        let sideways = scrolls.filter { $0.contentSize.width > $0.bounds.width && $0.contentSize.height <= $0.bounds.height }
        XCTAssertEqual(sideways.count, count, "horizontal scroll views", file: file, line: line)
        for scroll in sideways {
            XCTAssertNil(scroll.refreshControl, "a horizontal scroll view holds no pull to refresh", file: file, line: line)
            XCTAssertFalse(scroll.alwaysBounceVertical, "a horizontal scroll view does not bounce vertically", file: file, line: line)
        }
        let lists = scrolls.filter { $0.contentSize.height > $0.bounds.height }
        XCTAssertFalse(lists.isEmpty, "a vertical list", file: file, line: line)
        XCTAssertTrue(lists.allSatisfy { $0.refreshControl != nil }, "the vertical list keeps the pull to refresh", file: file, line: line)
    }

    /// The visible scroll views of `view` once a window has laid it out.
    private func layOut<V: View>(_ view: V) -> [UIScrollView] {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else {
            preconditionFailure("the tests run hosted in the app, which has a window scene")
        }
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: SnapshotMatrix.width, height: SnapshotMatrix.screenHeight)
        window.rootViewController = UIHostingController(rootView: view)
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            window.windowScene = nil
        }
        RunLoop.main.run(until: Date(timeIntervalSinceNow: 1))
        return scrollViews(in: window).filter { !$0.isHidden && $0.window != nil && $0.bounds.width > 0 }
    }

    private func scrollViews(in view: UIView) -> [UIScrollView] {
        ((view as? UIScrollView).map { [$0] } ?? []) + view.subviews.flatMap(scrollViews)
    }
}
