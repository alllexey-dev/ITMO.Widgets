import XCTest

extension XCUIApplication {
    /// The roots of the tab bar in the bar's order (`ShellTab`) by their route ids: today is `home`, profile `me`.
    static let shellTabs = ["home", "schedule", "recordbook", "sport", "me"]

    /// Taps the tab of `root` (`shell.root.<root>`); the system tab bar's buttons carry no identifiers.
    func selectTab(_ root: String) {
        guard let index = Self.shellTabs.firstIndex(of: root) else {
            preconditionFailure("no tab \(root)")
        }
        tabBars.firstMatch.buttons.element(boundBy: index).tap()
    }
}
