import SwiftUI

/// The tab container, the only type that knows how tabs are switched: by the native tab bar only, with no swipe
/// between tabs (design.md "Tab swipe").
struct ShellTabs<Root: View>: View {
    @Binding var selection: ShellTab
    var tabs: [ShellTab] = ShellTab.visible
    @ViewBuilder let root: (ShellTab) -> Root

    var body: some View {
        TabView(selection: $selection) {
            ForEach(tabs) { tab in
                root(tab)
                    .tabItem {
                        Label {
                            Text(verbatim: tab.title)
                        } icon: {
                            (selection == tab ? tab.selectedSymbol : tab.symbol).image
                        }
                    }
                    .tag(tab)
            }
        }
        .itmoTint()
    }
}
