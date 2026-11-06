import Shared
import SwiftUI

/// `me_isu_hub` (`AppRoutes.Isu`, docs/features/me.md), pushed from the profile: one group with the ISU messages and the
/// Wi-Fi sheet, each opened through the router.
struct IsuHubScreen: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        IsuHubContent(open: { router.open($0) })
            .itmoDetailHeader(title: AppStrings.string("me_isu_hub"), subtitle: nil)
    }
}

/// The screen without the router; snapshot tests pass a no-op.
struct IsuHubContent: View {
    var open: (any AppRoute) -> Void = { _ in }

    var body: some View {
        ItmoScreenStack {
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("me_isu_messages"),
                    subtitle: AppStrings.string("me_isu_messages_subtitle"),
                    symbol: .mail,
                    tone: .neutral,
                    chevron: true,
                    action: { open(AppRoutes.IsuMessages.shared) }
                )
                .accessibilityIdentifier("isu_messages_row")
                ItmoRow(
                    title: AppStrings.string("me_wifi"),
                    subtitle: AppStrings.string("me_wifi_subtitle"),
                    symbol: .wifi,
                    tone: .neutral,
                    chevron: true,
                    action: { open(AppRoutes.Wifi.shared) }
                )
                .accessibilityIdentifier("wifi_row")
            }
            .padding(.top, ItmoSpacing.compact)
        }
        .accessibilityIdentifier("isu_hub")
    }
}
