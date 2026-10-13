@testable import ITMOWidgets
import Shared
import SwiftUI
import XCTest

/// The App Group files the widgets read, written by the app's started graph: after the demo starts and
/// the pass screen fetches its pass, the container this process resolves holds `session-v1.json` and
/// `qr-pass-v1.json`, the QR widget's timeline reads a signed-in entry from them, and the session cleaners leave the
/// container itself in place. Without its metadata file the system drops the container as stale and gives the next
/// process (the widget extension) a new, empty one. Every value is the demo session's.
@MainActor
final class WidgetSnapshotsTests: XCTestCase {
    /// The container manager's record in every App Group container.
    private let containerMetadata = ".com.apple.mobile_container_manager.metadata.plist"
    private var session: SessionRepository!
    private var container: URL!
    private var removeWebsiteData: ((@escaping () -> Void) -> Void)!

    override func setUp() async throws {
        try await super.setUp()
        // The session cleaners run on every sign-out and demo start; WebKit's real removal hangs on a CI simulator.
        removeWebsiteData = AppPlatform.removeWebsiteData
        AppPlatform.removeWebsiteData = { $0() }
        session = try XCTUnwrap(IosKoin.shared.get(protocol: SessionRepository.self) as? SessionRepository)
        container = try XCTUnwrap(AppGroupSnapshot.container(), "the app resolves no App Group container")
        try await session.signOut()
    }

    override func tearDown() async throws {
        try await session.signOut()
        AppPlatform.removeWebsiteData = removeWebsiteData
        try await super.tearDown()
    }

    func testTheDemoSessionAndItsPassReachTheQrWidget() async throws {
        // Starting the demo runs every session cleaner first.
        try await session.startDemo()
        await waitUntil { SessionFile.read(fromContainer: self.container) != nil }
        XCTAssertEqual(SessionFile.read(fromContainer: container)?.demo, true)
        assertTheContainerIsKept()

        // The pass sheet fetches the pass, as opening it in the app does.
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = UIHostingController(rootView: QrPassSheet())
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        await waitUntil(timeout: 10) { QrPassSnapshot.read(fromContainer: self.container) != nil }
        let pass = try XCTUnwrap(QrPassSnapshot.read(fromContainer: container), "no qr-pass-v1.json")
        XCTAssertTrue(pass.demo)

        let entries = QrWidgetTimeline.entries(
            now: Date(),
            session: SessionFile.read(fromContainer: container),
            pass: pass,
            reveal: nil
        )
        XCTAssertEqual(entries.first?.content, .spoiler, "the widget shows the signed-in pass behind its spoiler")
    }

    func testSigningOutRemovesTheSnapshotsButKeepsTheContainer() async throws {
        try await session.startDemo()
        await waitUntil { SessionFile.read(fromContainer: self.container) != nil }

        try await session.signOut()

        XCTAssertNil(SessionFile.read(fromContainer: container))
        assertTheContainerIsKept()
    }

    private func assertTheContainerIsKept(file: StaticString = #filePath, line: UInt = #line) {
        let metadata = container.appendingPathComponent(containerMetadata).path
        XCTAssertTrue(FileManager.default.fileExists(atPath: metadata), "a cleaner removed \(containerMetadata)",
                      file: file, line: line)
    }

    private func waitUntil(timeout: TimeInterval = 5, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(50))
        }
    }
}
