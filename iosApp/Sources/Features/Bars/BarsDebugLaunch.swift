#if DEBUG
import OSLog
import Shared
import SwiftUI
import UIKit

/// The BARS session's entry points on the simulator until the recordbook reaches iOS (IO-09d2), Debug builds only:
///
/// - `-itmoBarsLogin` presents `BarsLoginSheet` once a real (not demo) session is signed in;
/// - `-itmoBarsRenew foreground` or `-itmoBarsRenew background` replaces the saved BARS header with one BARS rejects
///   and renews it as an expired session would: through the hidden `WKWebView`, or through the Keychain cookie copy
///   without WebKit (`BarsSessionCheck`).
///
/// Results go to unified logging (category `BarsSession`) and stdout (`simctl launch --console`): only the header's
/// length and expiry, never a value.
@MainActor
enum BarsDebugLaunch {
    static let loginArgument = "-itmoBarsLogin"
    static let renewArgument = "-itmoBarsRenew"

    private static let log = Logger(subsystem: Bundle.main.bundleIdentifier ?? "ITMOWidgets", category: "BarsSession")

    /// Runs what the launch arguments ask for; nothing without them.
    static func runIfRequested(arguments: [String] = CommandLine.arguments) {
        let login = arguments.contains(loginArgument)
        let renewal = arguments.firstIndex(of: renewArgument).flatMap { index in
            arguments.indices.contains(index + 1) ? arguments[index + 1] : nil
        }
        guard login || renewal != nil else { return }
        Task {
            guard await signedInWithAnAccount() else {
                report("BARS: needs a signed-in ITMO.ID session, not the demo")
                return
            }
            if login {
                await presentLogin()
            } else if let renewal {
                await renew(renewal)
            }
        }
    }

    private static func signedInWithAnAccount() async -> Bool {
        guard let session = IosKoin.shared.get(protocol: SessionRepository.self) as? SessionRepository else {
            return false
        }
        for await state in session.state {
            switch onEnum(of: state) {
            case let .signedIn(signedIn): return !signedIn.demo
            case .signedOut, .reauthenticationRequired: return false
            case .initializing, .signingOut: continue
            }
        }
        return false
    }

    private static func presentLogin() async {
        // The shell's first frame may not be on screen yet.
        for _ in 0..<20 {
            if let presenter = AppPlatform().topViewController() {
                var sheet: UIViewController?
                let view = BarsLoginSheet {
                    sheet?.dismiss(animated: true)
                    Task {
                        let session = (try? await check().report()) ?? "unknown"
                        report("BARS signed in: \(session)")
                    }
                }
                sheet = UIHostingController(rootView: view)
                if let sheet { presenter.present(sheet, animated: true) }
                return
            }
            try? await Task.sleep(for: .milliseconds(250))
        }
        report("BARS: no window to present the sign-in from")
    }

    private static func renew(_ mode: String) async {
        let check = check()
        do {
            switch mode {
            case "foreground": report("\(mode): \(try await check.renewInForeground())")
            case "background": report("\(mode): \(try await check.renewInBackground())")
            default: report("BARS: \(renewArgument) takes foreground or background")
            }
        } catch {
            report("BARS: the check failed")
        }
    }

    private static func check() -> BarsSessionCheck {
        guard let check = IosKoin.shared.get(type: BarsSessionCheck.self) as? BarsSessionCheck else {
            preconditionFailure("Koin resolved no BarsSessionCheck")
        }
        return check
    }

    private static func report(_ line: String) {
        log.notice("\(line, privacy: .public)")
        print(line)
    }
}
#endif
