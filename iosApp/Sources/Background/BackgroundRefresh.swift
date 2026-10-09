import OSLog
import Shared
import SwiftUI

/// The app refresh task of iOS (IO-14): the system wakes the app in the background now and then, and each wake runs
/// the Kotlin `BackgroundRunner` (widget snapshots, then the schedule change, marks and calendar steps) within its
/// deadline; the runner asks for the next wake at the end. `start()` (in `App.init`, after the graph) also runs it at
/// launch and on every return to the foreground, so Swift only starts it and hands it the system's task.
///
/// The system decides when the task runs and may delay it by hours; with Background App Refresh off or in Low Power
/// Mode it never runs. A Debug build launched with `-itmoRunRefresh` runs the entry point once more after the session
/// is read and posts a fixture schedule change and a fixture marks digest (tests cannot force the scheduler).
enum BackgroundRefresh {
    /// The task's identifier, also in the app's `BGTaskSchedulerPermittedIdentifiers` (`StableIdentifiersTests`).
    static let taskIdentifier = AppRefreshScheduler.companion.TASK_IDENTIFIER

    static let runArgument = "-itmoRunRefresh"

    private static let log = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ITMOWidgets",
        category: "BackgroundRefresh"
    )

    /// Builds the runner from the started graph and runs it at once, on every return to the foreground and on a
    /// check's `runOnce`.
    static func start() {
        _ = IosBackgroundRefresh.shared.start()
    }

    /// One run of the Kotlin runner; a cancelled Swift task (the system ending the background time) cancels it.
    @discardableResult
    static func run() async -> RefreshReport? {
        do {
            let report = try await IosBackgroundRefresh.shared.run()
            log.info("\(describe(report), privacy: .public)")
            return report
        } catch {
            log.error("The refresh failed: \(String(describing: type(of: error)), privacy: .public)")
            return nil
        }
    }

    /// `step=RESULT` per step in the runner's order, for logs and the Debug trigger.
    static func describe(_ report: RefreshReport) -> String {
        report.steps.map { "\($0.key)=\($0.result)" }.joined(separator: " ")
    }

    #if DEBUG
    /// Runs the entry point and posts the fixtures when the launch arguments ask for it; nothing otherwise.
    static func runIfRequested(arguments: [String] = CommandLine.arguments) {
        guard arguments.contains(runArgument) else { return }
        Task {
            if let report = await run() {
                print("Refresh: \(describe(report))")
            }
            try? await postFixtureScheduleChange()
            print("Refresh: posted the fixture schedule change")
            try? await postFixtureMarkDigest()
            print("Refresh: posted the fixture marks digest")
        }
    }
    #endif
}

extension Scene {
    /// Handles the app refresh task with the background runner.
    func backgroundRefresh() -> some Scene {
        backgroundTask(.appRefresh(BackgroundRefresh.taskIdentifier)) {
            await BackgroundRefresh.run()
        }
    }
}
