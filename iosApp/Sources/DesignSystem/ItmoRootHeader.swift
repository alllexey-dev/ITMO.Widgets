import SwiftUI

extension View {
    /// The header of a tab's root screen: the title with its one-line subtitle on the leading side and `actions` in
    /// the same row, centred on them. It is static (owner, 2026-10-13): the system's large title collapses to
    /// `.inline` on scroll, so the root hides the navigation bar and draws the header as a bar of its own
    /// (`safeAreaBar`, with the scroll edge effect) that the content scrolls under. The navigation title stays for the
    /// back button of a pushed screen. The bar is opaque: scrolled content never shows through it. With
    /// `titleMenu` the title is a button with a chevron (the schedule's month sheet), `titleMenuLabel` its hint.
    func itmoRootHeader<Actions: View>(
        title: String,
        subtitle: String?,
        titleMenu: (() -> Void)? = nil,
        titleMenuLabel: String? = nil,
        @ViewBuilder actions: () -> Actions = { EmptyView() }
    ) -> some View {
        modifier(
            ItmoRootHeader(
                title: title, subtitle: subtitle, titleMenu: titleMenu, titleMenuLabel: titleMenuLabel, actions: actions()
            )
        )
    }
}

private struct ItmoRootHeader<Actions: View>: ViewModifier {
    let title: String
    let subtitle: String?
    let titleMenu: (() -> Void)?
    let titleMenuLabel: String?
    let actions: Actions

    func body(content: Content) -> some View {
        content
            .navigationTitle(Text(verbatim: title))
            .toolbarVisibility(.hidden, for: .navigationBar)
            .safeAreaBar(edge: .top) {
                HStack(spacing: ItmoSpacing.content) {
                    if let titleMenu {
                        Button(action: titleMenu) { titleBlock(chevron: true).contentShape(.rect) }
                            .buttonStyle(.plain)
                            .accessibilityHint(Text(verbatim: titleMenuLabel ?? ""))
                    } else {
                        titleBlock(chevron: false)
                    }
                    Spacer(minLength: 0)
                    actions
                }
                .padding(.horizontal, ItmoSpacing.screenMargin)
                .padding(.vertical, ItmoSpacing.compact)
                // Opaque: content scrolled under the header never shows through it.
                .background(Color(.systemGroupedBackground).ignoresSafeArea(edges: .top))
            }
    }

    private func titleBlock(chevron: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Text(verbatim: title)
                    .font(.itmo(.displaySmall, emphasized: true))
                if chevron {
                    AppSymbol.expandMore.image
                        .font(.itmo(.titleMedium, emphasized: true))
                        .foregroundStyle(ItmoColor.primary)
                        .accessibilityHidden(true)
                }
            }
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
