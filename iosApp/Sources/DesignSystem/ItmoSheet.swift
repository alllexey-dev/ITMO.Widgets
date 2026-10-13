import SwiftUI

/// The three sheet heights of the app (docs/design.md, SwiftUI screens). A sheet never goes under the status bar.
enum ItmoSheetHeight: Hashable, Sendable {
    /// As tall as the content: short content is never cut, long content scrolls up to the full height.
    case content
    /// 0.82 of the screen (the bell), draggable to full.
    case tall
    /// The full height below the status bar.
    case full

    /// The share of the screen a `tall` sheet opens at.
    static let tallFraction: CGFloat = 0.82
}

/// The body of a sheet: an inline title with the close button, scrolling content at the sheet margins, the grabber
/// and the detents of `height`. Put it inside `.sheet { }`; the close button dismisses the presentation.
struct ItmoSheet<Content: View>: View {
    let title: String
    var height: ItmoSheetHeight = .content
    @ViewBuilder var content: Content

    @Environment(\.dismiss) private var dismiss
    /// The content's height and the bars around it, measured once laid out (`.content`).
    @State private var measured: CGFloat = 0
    @State private var insets = EdgeInsets()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ItmoSpacing.content) {
                    content
                }
                .padding(.horizontal, ItmoKit.margin)
                .padding(.bottom, ItmoSpacing.group)
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { measured = $0 }
            }
            .onGeometryChange(for: EdgeInsets.self) { $0.safeAreaInsets } action: { insets = $0 }
            .scrollBounceBehavior(.basedOnSize)
            .navigationTitle(Text(verbatim: title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .close) { dismiss() }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                        .accessibilityIdentifier("sheet.close")
                }
            }
            .environment(\.itmoOnSheet, true)
            .background(Color(.systemGroupedBackground))
        }
        .presentationDetents(detents)
        .presentationDragIndicator(.visible)
        .presentationBackground(Color(.systemGroupedBackground))
    }

    private var detents: Set<PresentationDetent> {
        switch height {
        case .content:
            // Before the first layout the sheet opens at half height; then it fits the content.
            measured > 0 ? [.height(measured + insets.top + insets.bottom)] : [.medium]
        case .tall: [.fraction(ItmoSheetHeight.tallFraction), .large]
        case .full: [.large]
        }
    }
}
