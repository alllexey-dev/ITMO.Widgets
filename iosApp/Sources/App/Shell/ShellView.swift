import SwiftUI

/// The app's root: the session gate (the ITMO.ID sign-in page while signed out), then the tab bar with one
/// `NavigationStack` per tab, the demo banner above the tab bar while the demo session is open, and the shell's sheets.
struct ShellView: View {
    @Bindable var router: AppRouter
    let session: ShellSession

    var body: some View {
        content
            .onChange(of: session.state, initial: true) { _, state in
                router.sessionChanged(state.sessionState, onboarding: state.onboarding)
            }
            .task { await session.follow() }
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
            if let gateway = session.gateway {
                ItmoSignInScreen(gateway: gateway)
            } else {
                FixtureSignInGate(signIn: session.signIn)
            }
        case .demo, .signedIn:
            ShellTabs(selection: $router.selectedTab) { tab in
                ShellStack(
                    tab: tab,
                    router: router,
                    isDemo: session.state == .demo,
                    leaveDemo: { session.signOut() },
                    signOut: { session.signOut() }
                )
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
    /// The sign-out of the me root; none in snapshot tests.
    var signOut: (() -> Void)?

    var body: some View {
        // The banner sits under the stack, not in a `safeAreaInset`: the inset never reaches the screens a stack
        // pushes, so the end of a pushed Compose screen would scroll behind the banner (IO-21).
        VStack(spacing: 0) {
            NavigationStack(path: path) {
                FixtureRootScreen(tab: tab, router: router, signOut: signOut)
                    .shellChrome(.compose)
                    .navigationDestination(for: ShellDestination.self) { destination in
                        Routes.view(for: destination)
                            .shellChrome(destination.chrome)
                    }
            }
            if isDemo {
                ItmoDemoBanner(signIn: leaveDemo)
            }
        }
    }

    private var path: Binding<[ShellDestination]> {
        Binding(get: { router.path(of: tab) }, set: { router.setPath($0, of: tab) })
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
