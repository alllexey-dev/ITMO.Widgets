import SwiftUI

/// The strip above the tab bar while the demo session is open (`DemoMode`): the demo notice and a sign-in button,
/// as on Android. At accessibility text sizes the button moves under the notice.
struct ItmoDemoBanner: View {
    var text: String = AppStrings.string("demo_banner_text")
    var signInTitle: String = AppStrings.string("demo_banner_sign_in")
    let signIn: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 0) {
                    notice
                    button.padding(.leading, -ItmoSpacing.content)
                }
            } else {
                HStack(spacing: ItmoSpacing.compact) {
                    notice
                    Spacer(minLength: 0)
                    button
                }
            }
        }
        .padding(.leading, ItmoSpacing.screenMargin)
        .padding(.trailing, ItmoSpacing.related)
        .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget, alignment: .leading)
        .background(ItmoColor.secondaryContainer)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("kit.demoBanner")
    }

    private var notice: some View {
        Text(verbatim: text)
            .font(.itmo(.bodyMedium))
            .foregroundStyle(ItmoColor.onSecondaryContainer)
            .padding(.vertical, ItmoSpacing.compact)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var button: some View {
        Button(action: signIn) {
            Text(verbatim: signInTitle)
                .font(.itmo(.labelLarge))
                .padding(.horizontal, ItmoSpacing.content)
                .frame(minWidth: ItmoMetrics.touchTarget, minHeight: ItmoMetrics.touchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(ItmoColor.onSecondaryContainer)
        .fixedSize()
        .accessibilityIdentifier("kit.demoBanner.signIn")
    }
}
