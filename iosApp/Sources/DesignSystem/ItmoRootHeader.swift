import SwiftUI

extension View {
    /// The header of a tab's root screen: the title with its one-line subtitle on the leading side and `actions` in
    /// the same row, centred on them. It is static (owner, 2026-10-13): the system's large title collapses to
    /// `.inline` on scroll, so the root hides the navigation bar and draws the header as a bar of its own
    /// (`safeAreaBar`, with the scroll edge effect) that the content scrolls under. The navigation title stays for the
    /// back button of a pushed screen.
    func itmoRootHeader<Actions: View>(
        title: String,
        subtitle: String?,
        @ViewBuilder actions: () -> Actions = { EmptyView() }
    ) -> some View {
        modifier(ItmoRootHeader(title: title, subtitle: subtitle, actions: actions()))
    }
}

private struct ItmoRootHeader<Actions: View>: ViewModifier {
    let title: String
    let subtitle: String?
    let actions: Actions

    func body(content: Content) -> some View {
        content
            .navigationTitle(Text(verbatim: title))
            .toolbarVisibility(.hidden, for: .navigationBar)
            .safeAreaBar(edge: .top) {
                HStack(spacing: ItmoSpacing.content) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(verbatim: title)
                            .font(.itmo(.displaySmall, emphasized: true))
                        if let subtitle {
                            Text(verbatim: subtitle)
                                .font(.itmo(.titleSmall))
                                .foregroundStyle(.secondary)
                        }
                    }
                    .lineLimit(1)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("kit.rootHeader")
                    Spacer(minLength: 0)
                    actions
                }
                .padding(.horizontal, ItmoSpacing.screenMargin)
                .padding(.vertical, ItmoSpacing.compact)
            }
    }
}

/// The bell of the today header, the only screen that shows it; `badge` counts the events the user has not seen.
struct ItmoBellButton: View {
    let label: String
    var badge = 0
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppSymbol.notification.image
                .overlay(alignment: .topTrailing) {
                    if badge > 0 {
                        Text(verbatim: "\(badge)")
                            .font(.itmo(.labelSmall, emphasized: true))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 16, minHeight: 16)
                            .background(ItmoColor.error, in: .capsule)
                            .offset(x: 9, y: -7)
                    }
                }
                .frame(width: 28, height: 28)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityValue(Text(verbatim: badge > 0 ? "\(badge)" : ""))
        .accessibilityIdentifier("shell.bell")
    }
}
