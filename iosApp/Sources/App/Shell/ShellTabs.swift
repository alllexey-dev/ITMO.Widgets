import SwiftUI

/// The tab container (DECISIONS.md, Shell and navigation): the glass tab bar of iOS 26 with a label on every tab and
/// the pass accessory above it while today is selected. Tabs switch by the bar only, with no swipe between them
/// (design.md "Tab swipe"); a pushed screen keeps the bar.
struct ShellTabs<Root: View>: View {
    @Binding var selection: ShellTab
    var showPass = true
    var tabs: [ShellTab] = ShellTab.visible
    /// Opens the pass sheet: the accessory's only action.
    let openPass: () -> Void
    @ViewBuilder let root: (ShellTab) -> Root

    var body: some View {
        TabView(selection: $selection) {
            ForEach(tabs) { tab in
                Tab(value: tab) {
                    root(tab)
                } label: {
                    Label {
                        Text(verbatim: tab.title)
                    } icon: {
                        tab.symbol.image
                    }
                }
            }
        }
        .tabViewBottomAccessory(isEnabled: selection == .today && showPass) {
            ShellPassAccessory(action: openPass)
        }
        .itmoTint()
    }
}

/// The pass accessory: one tap from today to the pass sheet.
struct ShellPassAccessory: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label {
                Text(verbatim: AppStrings.string("qr_pass_title"))
            } icon: {
                AppSymbol.qrCode.image
            }
            .font(.itmo(.bodyMedium, emphasized: true))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("shell.passAccessory")
    }
}
