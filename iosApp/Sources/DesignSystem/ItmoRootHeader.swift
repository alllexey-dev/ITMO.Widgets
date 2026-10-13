import SwiftUI

extension View {
    /// The header of a tab's root screen: the navigation bar in `.inlineLarge` mode, the title with its one-line
    /// subtitle on the leading side and the toolbar items in the same row, centred on them; once the content's scroll
    /// view leaves the top, the bar switches to `.inline` (a small centred title over the scroll edge effect).
    /// Toolbar items stay the screen's own `.toolbar`.
    func itmoRootHeader(title: String, subtitle: String?) -> some View {
        modifier(ItmoRootHeader(title: title, subtitle: subtitle))
    }
}

private struct ItmoRootHeader: ViewModifier {
    let title: String
    let subtitle: String?

    @State private var scrolled = false

    func body(content: Content) -> some View {
        content
            .navigationTitle(Text(verbatim: title))
            .navigationSubtitle(Text(verbatim: subtitle ?? ""))
            .toolbarTitleDisplayMode(scrolled ? .inline : .inlineLarge)
            .onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top > 8
            } action: { _, isScrolled in
                scrolled = isScrolled
            }
    }
}

/// The bell of the today header, the only screen that shows it.
struct ItmoBellButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppSymbol.notification.image
        }
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityIdentifier("shell.bell")
    }
}
