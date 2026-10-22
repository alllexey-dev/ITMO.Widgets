@testable import ITMOWidgets
import XCTest

@MainActor
final class ScheduleFeedScrollTests: XCTestCase {
    func testACompletedJumpPositionsTwiceAndAcknowledgesOnce() async {
        var positions = 0
        var completions = 0

        await ScheduleFeedScroll.settle(scroll: { positions += 1 }, completed: { completions += 1 })

        XCTAssertEqual(positions, 2)
        XCTAssertEqual(completions, 1)
    }

    func testANewerJumpCancelsTheOldPositionAndAcknowledgement() async {
        let positioned = expectation(description: "First positioning pass")
        var oldPositions = 0
        var oldCompletions = 0
        let old = Task {
            await ScheduleFeedScroll.settle(scroll: {
                oldPositions += 1
                positioned.fulfill()
            }, completed: { oldCompletions += 1 })
        }
        await fulfillment(of: [positioned], timeout: 2)
        old.cancel()
        var newPositions = 0
        var newCompletions = 0

        await ScheduleFeedScroll.settle(scroll: { newPositions += 1 }, completed: { newCompletions += 1 })
        await old.value

        XCTAssertEqual(oldPositions, 1)
        XCTAssertEqual(oldCompletions, 0)
        XCTAssertEqual(newPositions, 2)
        XCTAssertEqual(newCompletions, 1)
    }

    func testOpeningDetailsInvalidatesTheJumpBeforeItsSwiftUITaskIsCancelled() async {
        let positioned = expectation(description: "First positioning pass")
        var positions = 0
        var completions = 0
        var current = true
        let jump = Task {
            await ScheduleFeedScroll.settle(
                scroll: { positions += 1; positioned.fulfill() },
                completed: { completions += 1 },
                isCurrent: { current }
            )
        }
        await fulfillment(of: [positioned], timeout: 2)
        // Model-state observation crosses Kotlin/Swift asynchronously. A lesson tap cancels the local intent now.
        current = false
        await jump.value
        XCTAssertEqual(positions, 1)
        XCTAssertEqual(completions, 0)
    }

    func testAnAlreadyInvalidatedJumpNeverScrolls() async {
        var positions = 0
        var completions = 0
        await ScheduleFeedScroll.settle(
            scroll: { positions += 1 }, completed: { completions += 1 }, isCurrent: { false }
        )
        XCTAssertEqual(positions, 0)
        XCTAssertEqual(completions, 0)
    }
}
