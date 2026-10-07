import Shared
import SwiftUI

/// The web sign-in of a Debug build launched with `-itmoWebLoginFixture` (UI tests, IO-08b): once the tabs are on
/// screen the sheet opens by itself, its ViewModel answers from `WebLoginIosFixture` instead of Backend, and the scan
/// button hands over the fixture's link, since the simulator has no camera. A Release build ignores the argument.
enum WebLoginFixture {
    static let argument = "-itmoWebLoginFixture"

    static var isOn: Bool {
        #if DEBUG
        CommandLine.arguments.contains(argument)
        #else
        false
        #endif
    }

    /// The fixture's ViewModel in `store`; nil without the argument.
    static func viewModel(store: ScreenViewModelStore) -> WebLoginViewModel? {
        guard isOn else { return nil }
        guard let time = IosKoin.shared.get(protocol: AcademicTimeProvider.self) as? AcademicTimeProvider else {
            preconditionFailure("Koin resolved no AcademicTimeProvider")
        }
        let model = store.getOrCreate(type: WebLoginViewModel.self) {
            WebLoginIosFixture.shared.viewModel(time: time)
        }
        return model as? WebLoginViewModel
    }
}

extension View {
    /// Opens the web sign-in once on the tabs of a fixture build; does nothing otherwise.
    func webLoginFixtureEntry(router: AppRouter) -> some View {
        task {
            guard WebLoginFixture.isOn else { return }
            router.open(AppRoutes.WebLogin(code: nil))
        }
    }
}
