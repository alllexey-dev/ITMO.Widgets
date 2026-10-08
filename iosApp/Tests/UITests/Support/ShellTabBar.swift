import XCTest

extension XCUIApplication {
    /// The roots of the tab bar in Android's order (`ShellTab`), the recordbook first since IO-09d2.
    static let shellTabs = ["recordbook", "schedule", "home", "sport", "me"]

    /// Taps the tab of `root` (`shell.root.<root>`); the system tab bar's buttons carry no identifiers.
    func selectTab(_ root: String) {
        guard let index = Self.shellTabs.firstIndex(of: root) else {
            preconditionFailure("no tab \(root)")
        }
        tabBars.firstMatch.buttons.element(boundBy: index).tap()
    }
}
