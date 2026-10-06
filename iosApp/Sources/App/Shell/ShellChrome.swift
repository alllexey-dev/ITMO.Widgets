import SwiftUI
import UIKit

/// Who draws a pushed screen's top bar (owner may veto at T12).
///
/// - `compose`: a CMP route draws its own DS-03 top bar, so the SwiftUI bar is hidden.
/// - `native`: a SwiftUI screen uses the navigation bar of its stack.
enum ShellChrome {
    case compose
    case native
}

extension View {
    /// The chrome of a screen in a tab's stack. A hidden navigation bar also turns off the edge swipe back, so the
    /// compose chrome turns it on again for this stack.
    @ViewBuilder
    func shellChrome(_ chrome: ShellChrome) -> some View {
        switch chrome {
        case .compose:
            toolbar(.hidden, for: .navigationBar)
                .background(InteractivePopRestorer().frame(width: 0, height: 0).accessibilityHidden(true))
        case .native:
            self
        }
    }
}

/// Re-enables the edge swipe back of the enclosing `UINavigationController` while the bar is hidden: UIKit drops
/// the gesture with the bar unless the gesture's delegate allows it, and this controller is that delegate while its
/// screen is on top.
private struct InteractivePopRestorer: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller { Controller() }

    func updateUIViewController(_ controller: Controller, context: Context) {}

    final class Controller: UIViewController, UIGestureRecognizerDelegate {
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard let gesture = navigationController?.interactivePopGestureRecognizer else { return }
            gesture.delegate = self
            gesture.isEnabled = true
        }

        /// Only above the root: a swipe on the root would leave the stack in a broken transition.
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            (navigationController?.viewControllers.count ?? 0) > 1
        }
    }
}
