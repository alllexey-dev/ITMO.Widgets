import SwiftUI
import UIKit

/// A Compose Multiplatform screen in SwiftUI (recipe ios-cmp-host): the `UIViewController` a
/// `screens/<Feature>Screens.kt` factory builds, made once per view identity and never updated, since the screen's
/// state lives in its Koin ViewModel.
///
/// It fills its frame and ignores the safe area, so the Compose surface runs under the status bar and the tab bar,
/// and Compose's `WindowInsets` report both: the factory pads its content by `WindowInsets.safeDrawing`. Only UIKit's
/// safe area reaches the controller, never a SwiftUI `safeAreaInset`, so shell chrome around a stack (the demo
/// banner) sits beside it, not over it. A screen inside a stack hides the navigation bar (`ShellChrome.compose`).
struct ComposeHost: View {
    let make: @MainActor () -> UIViewController

    var body: some View {
        ComposeController(make: make)
            .ignoresSafeArea()
    }
}

private struct ComposeController: UIViewControllerRepresentable {
    let make: @MainActor () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        make()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
