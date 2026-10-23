import Shared
import SwiftUI

/// The app's root: the session gate (the sign-in screen while signed out, the first-run flow before the tabs of a
/// new account), then the tab bar with one `NavigationStack` per tab, the demo banner above the tab bar while the demo
/// session is open, and the shell's sheets; all of it light or dark as the appearance page says (`ThemeSpec.mode`).
struct ShellView: View {
    @Bindable var router: AppRouter
    let session: ShellSession
    @State private var showPass = false
    @State private var themeMode = ThemeMode.system
    @State private var updateOffer = AppUpdateOffer()

    var body: some View {
        content
            .onChange(of: session.state, initial: true) { _, state in
                router.sessionChanged(state.sessionState, onboarding: session.onboarding)
            }
            .onChange(of: session.onboarding) { _, onboarding in
                router.sessionChanged(session.state.sessionState, onboarding: onboarding)
            }
            .task { await session.follow() }
            .task {
                guard session.gateway != nil else { showPass = true; return }
                guard let visibility = IosKoin.shared.get(protocol: HomePassVisibility.self) as? HomePassVisibility else { return }
                for await visible in visibility.observeHomePassVisible() {
                    showPass = visible.boolValue
                }
            }
            .task {
                guard let appearance = IosKoin.shared.get(type: AppearancePreferences.self) as? AppearancePreferences else { return }
                for await theme in appearance.observeTheme() {
                    themeMode = theme.mode
                }
            }
            .sheet(item: $router.sheet) { sheet in
                // Route sheets open further routes and answer their opener through the router.
                ShellSheetView(sheet: sheet)
                    .id(sheet.id)
                    .environment(router)
            }
            // On the root, so the window and every sheet and alert take it.
            .preferredColorScheme(themeMode.colorScheme)
    }

    @ViewBuilder
    private var content: some View {
        switch session.surface {
        case .loading:
            ItmoLoadingView()
                .frame(maxHeight: .infinity)
                .accessibilityIdentifier("shell.gate.loading")
        case .auth:
            if session.gateway != nil {
                AuthScreen()
            } else {
                FixtureSignInGate(signIn: session.signIn)
            }
        case .onboarding:
            OnboardingScreen(finished: session.onboardingFinished)
        case let .tabs(demo):
            ShellTabs(selection: $router.selectedTab, showPass: showPass && !router.isFullScreen(router.selectedTab), openPass: { router.open(AppRoutes.QrPass.shared) }) { tab in
                ShellStack(
                    tab: tab,
                    router: router,
                    isDemo: demo,
                    leaveDemo: { session.signOut() }
                )
            }
            .onAppear { router.shellMounted(true) }
            .onDisappear { router.shellMounted(false) }
            .appUpdateOffer(updateOffer, checks: checksForUpdate)
        }
    }
}

extension ShellView {
    /// Android's `ShellGate.checksForUpdate`: the tabs of a real session, never the demo or a fixture session.
    private var checksForUpdate: Bool {
        session.gateway != nil
            && ShellGate.shared.checksForUpdate(session: session.state.sessionState, onboarding: session.onboarding)
    }
}

/// One tab's stack: its root, the screens the router pushed, and the demo banner above the tab bar. Every root draws
/// the native root header (`itmoRootHeader`); a pushed screen has the stack's navigation bar.
struct ShellStack: View {
    let tab: ShellTab
    @Bindable var router: AppRouter
    let isDemo: Bool
    let leaveDemo: () -> Void

    var body: some View {
        // The banner sits under the stack, not in a `safeAreaInset`: the inset never reaches the screens a stack
        // pushes, so the end of a pushed screen would scroll behind the banner.
        VStack(spacing: 0) {
            NavigationStack(path: path) {
                root
                    .navigationDestination(for: ShellDestination.self) { destination in
                        Routes.view(for: destination)
                            .toolbar(destination.isFullScreen ? .hidden : .automatic, for: .tabBar)
                    }
            }
            // SwiftUI screens open further routes and leave the stack through the router (settings).
            .environment(router)
            if isDemo, !router.isFullScreen(tab) {
                ItmoDemoBanner(signIn: leaveDemo)
            }
        }
    }

    /// The tab's root, each a SwiftUI screen over its shared ViewModels.
    @ViewBuilder
    private var root: some View {
        switch tab {
        case .today:
            TodayScreen(router: router)
        case .schedule:
            ScheduleScreen(router: router)
        case .recordbook:
            RecordbookScreen(router: router)
        case .sport:
            SportTabScreen(router: router)
        case .profile:
            MeTabScreen(router: router)
        }
    }

    private var path: Binding<[ShellDestination]> {
        Binding(get: { router.path(of: tab) }, set: { router.setPath($0, of: tab) })
    }
}

/// A shell sheet. The damaged-link message is a content-height kit sheet, a route sheet brings its own content and
/// detents (`Routes.sheetView`).
struct ShellSheetView: View {
    let sheet: ShellSheet

    var body: some View {
        switch sheet {
        case .linkUnavailable: messageSheet
        case let .route(destination): Routes.sheetView(for: destination)
        }
    }

    private var messageSheet: some View {
        ItmoSheet(title: "", height: .content) {
            ItmoEmptyView(
                symbol: .error,
                title: AppStrings.string("app_link_unavailable_title"),
                description: AppStrings.string("app_link_unavailable_text")
            )
        }
        .accessibilityIdentifier("shell.sheet.linkUnavailable")
    }
}

extension ThemeMode {
    /// What `preferredColorScheme` takes: nil follows the system.
    var colorScheme: ColorScheme? {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }
}
