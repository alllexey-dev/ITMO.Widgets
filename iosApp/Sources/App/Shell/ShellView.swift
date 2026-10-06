import SwiftUI

/// The app's root: the session gate, then the tab bar with one `NavigationStack` per tab, the demo banner above the
/// tab bar while the demo session is open, and the shell's sheets.
struct ShellView: View {
    @Bindable var router: AppRouter
    let session: ShellSession

    var body: some View {
        content
            .onChange(of: session.state.isReady, initial: true) { _, ready in
                router.sessionChanged(ready: ready)
            }
            .sheet(item: $router.sheet) { sheet in
                ShellSheetView(sheet: sheet)
            }
    }

    @ViewBuilder
    private var content: some View {
        switch session.state {
        case .loading:
            ItmoLoadingView()
                .frame(maxHeight: .infinity)
                .accessibilityIdentifier("shell.gate.loading")
        case .signedOut:
            FixtureSignInGate(signIn: session.signIn)
        case .demo, .signedIn:
            ShellTabs(selection: $router.selectedTab) { tab in
                ShellStack(tab: tab, router: router, isDemo: session.state == .demo, leaveDemo: session.leaveDemo)
            }
            .onAppear { router.shellMounted(true) }
            .onDisappear { router.shellMounted(false) }
        }
    }
}

/// One tab's stack: its root, the screens the router pushed, and the demo banner above the tab bar.
struct ShellStack: View {
    let tab: ShellTab
    @Bindable var router: AppRouter
    let isDemo: Bool
    let leaveDemo: () -> Void

    var body: some View {
        NavigationStack(path: path) {
            FixtureRootScreen(tab: tab, router: router)
                .shellChrome(.compose)
                .navigationDestination(for: ShellDestination.self) { destination in
                    ShellDestinationView(destination: destination)
                        .shellChrome(destination.chrome)
                }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if isDemo {
                ItmoDemoBanner(signIn: leaveDemo)
            }
        }
    }

    private var path: Binding<[ShellDestination]> {
        Binding(get: { router.path(of: tab) }, set: { router.setPath($0, of: tab) })
    }
}

private struct ShellDestinationView: View {
    let destination: ShellDestination

    var body: some View {
        switch destination {
        case .qrPass: FixtureQrPassScreen()
        }
    }
}

/// A shell sheet: half height first, full height on a drag.
struct ShellSheetView: View {
    let sheet: ShellSheet
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                switch sheet {
                case .linkUnavailable:
                    ItmoEmptyView(
                        symbol: .error,
                        title: AppStrings.string("app_link_unavailable_title"),
                        description: AppStrings.string("app_link_unavailable_text")
                    )
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        AppSymbol.close.image
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                    .accessibilityIdentifier("sheet.close")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .accessibilityIdentifier("shell.sheet.\(sheet.rawValue)")
    }
}
