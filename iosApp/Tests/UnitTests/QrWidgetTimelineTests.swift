@testable import ITMOWidgets
import WidgetKit
import XCTest

/// The QR widget's states from its App Group files (IO-10a): signed out, spoiler, revealed until the reveal ends,
/// expired at the pass's deadline, and the demo pass.
final class QrWidgetTimelineTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_788_250_000)
    private let session = SessionFile(isu: 123_456, demo: false, alertsAllowed: false)
    private let matrix = [[true, false], [false, true]]

    func testWithoutASessionTheWidgetIsSignedOut() {
        let entries = QrWidgetTimeline.entries(now: now, session: nil, pass: pass(expiresIn: 600), reveal: nil)
        XCTAssertEqual(entries, [QrWidgetEntry(date: now, content: .signedOut)])
    }

    func testWithoutAValidPassTheWidgetAsksToRefresh() {
        XCTAssertEqual(
            QrWidgetTimeline.entries(now: now, session: session, pass: nil, reveal: nil),
            [QrWidgetEntry(date: now, content: .expired)]
        )
        XCTAssertEqual(
            QrWidgetTimeline.entries(now: now, session: session, pass: pass(expiresIn: 0), reveal: nil),
            [QrWidgetEntry(date: now, content: .expired)]
        )
    }

    func testAValidPassShowsTheSpoilerUntilItExpires() {
        let pass = pass(expiresIn: 600)
        XCTAssertEqual(
            QrWidgetTimeline.entries(now: now, session: session, pass: pass, reveal: nil),
            [QrWidgetEntry(date: now, content: .spoiler), QrWidgetEntry(date: pass.expiresAt, content: .expired)]
        )
    }

    func testARevealShowsTheCodeUntilItEnds() {
        let pass = pass(expiresIn: 600)
        let reveal = QrWidgetReveal.startingAt(now.addingTimeInterval(-10))
        XCTAssertEqual(
            QrWidgetTimeline.entries(now: now, session: session, pass: pass, reveal: reveal),
            [
                QrWidgetEntry(date: now, content: .revealed(matrix: matrix, demo: false)),
                QrWidgetEntry(date: reveal.revealedUntil, content: .spoiler),
                QrWidgetEntry(date: pass.expiresAt, content: .expired),
            ]
        )
    }

    func testARevealPastThePassDeadlineEndsWithTheExpiredState() {
        let pass = pass(expiresIn: 10)
        XCTAssertEqual(
            QrWidgetTimeline.entries(now: now, session: session, pass: pass, reveal: .startingAt(now)),
            [
                QrWidgetEntry(date: now, content: .revealed(matrix: matrix, demo: false)),
                QrWidgetEntry(date: pass.expiresAt, content: .expired),
            ]
        )
    }

    func testAnEndedRevealShowsTheSpoiler() {
        let entries = QrWidgetTimeline.entries(
            now: now, session: session, pass: pass(expiresIn: 600), reveal: .startingAt(now.addingTimeInterval(-60))
        )
        XCTAssertEqual(entries.first, QrWidgetEntry(date: now, content: .spoiler))
    }

    func testWithTheSpoilerOffTheCodeShowsAtOnce() {
        var pass = pass(expiresIn: 600)
        pass.spoiler = false
        XCTAssertEqual(
            QrWidgetTimeline.entries(now: now, session: session, pass: pass, reveal: nil),
            [
                QrWidgetEntry(date: now, content: .revealed(matrix: matrix, demo: false)),
                QrWidgetEntry(date: pass.expiresAt, content: .expired),
            ]
        )
    }

    func testTheDemoPassIsMarked() {
        let demoSession = SessionFile(isu: nil, demo: true, alertsAllowed: false)
        let entries = QrWidgetTimeline.entries(
            now: now, session: demoSession, pass: pass(expiresIn: 600, demo: true), reveal: .startingAt(now)
        )
        XCTAssertEqual(entries.first, QrWidgetEntry(date: now, content: .revealed(matrix: matrix, demo: true)))
    }

    func testTheTimelineAsksAgainWithinAnHour() {
        let timeline = QrWidgetTimeline.timeline(now: now, session: session, pass: pass(expiresIn: 600), reveal: nil)
        XCTAssertEqual(timeline.policy, .after(now.addingTimeInterval(3600)))
    }

    private func pass(expiresIn seconds: TimeInterval, demo: Bool = false) -> QrPassSnapshot {
        QrPassSnapshot(
            generatedAt: now.addingTimeInterval(-30),
            expiresAt: now.addingTimeInterval(seconds),
            demo: demo,
            matrix: matrix
        )
    }
}
