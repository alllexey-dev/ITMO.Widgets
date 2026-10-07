import Shared
import SwiftUI
import UIKit

/// Android's `AuthFragment` over the shared `AuthViewModel` (IO-07b): the logo, the app name, what the app does, the
/// ITMO.ID sign-in (`ItmoSignInScreen` above it) and the refresh-token sign-in, with the notices under them. Five taps
/// on the logo open the demo session (`DemoEntryTaps`); the logo stays decorative for VoiceOver, as on Android.
struct AuthScreen: View {
    @State private var model = ObservableViewModel(AuthViewModel.self, state: \.uiState)
    @State private var showsSignInPage = false
    @State private var asksRefreshToken = false
    @State private var refreshToken = ""

    var body: some View {
        AuthContent(
            state: model.state,
            logoTapped: { model.viewModel.onLogoTap() },
            signIn: {
                model.viewModel.clearError()
                showsSignInPage = true
            },
            signInWithRefreshToken: { asksRefreshToken = true }
        )
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case .demoStarted:
                // Android's confirm haptic and `demo_entered` toast; the demo session replaces this screen.
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                AccessibilityNotification.Announcement(AppStrings.string("demo_entered")).post()
            }
        }
        .fullScreenCover(isPresented: $showsSignInPage, onDismiss: model.viewModel.clearError) {
            ItmoSignInScreen { showsSignInPage = false }
        }
        .alert(Text(verbatim: AppStrings.string("auth_manual_dialog_title")), isPresented: $asksRefreshToken) {
            SecureField(AppStrings.string("auth_manual_token_hint"), text: $refreshToken)
                .textContentType(.oneTimeCode)
                .accessibilityIdentifier("auth.refreshToken.field")
            Button(role: .cancel) {
                refreshToken = ""
            } label: {
                Text(verbatim: AppStrings.string("common_cancel"))
            }
            Button {
                let token = refreshToken
                refreshToken = ""
                model.viewModel.signInWithRefreshToken(refreshToken: token)
            } label: {
                Text(verbatim: AppStrings.string("auth_sign_in"))
            }
            .disabled(refreshToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityIdentifier("auth.refreshToken.signIn")
        } message: {
            Text(verbatim: AppStrings.string("auth_manual_dialog_description"))
        }
    }
}

/// The sign-in screen for one `AuthUiState`, without the ViewModel (snapshot tests render it directly).
struct AuthContent: View {
    let state: AuthUiState
    let logoTapped: () -> Void
    let signIn: () -> Void
    let signInWithRefreshToken: () -> Void

    @ScaledMetric(relativeTo: .largeTitle) private var logoSize: CGFloat = 88

    var body: some View {
        Group {
            if state.initializing {
                ItmoLoadingView(title: AppStrings.string("auth_initializing"))
                    .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    content
                        .padding(.horizontal, ItmoSpacing.section)
                        .padding(.top, 64)
                        .padding(.bottom, ItmoSpacing.statePadding)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemBackground))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("auth.screen")
    }

    private var signInEnabled: Bool {
        !state.manualLoginInProgress && !state.sessionTransitionInProgress
    }

    private var content: some View {
        VStack(spacing: 0) {
            AuthLogo()
                .frame(width: logoSize, height: logoSize)
                .contentShape(Circle())
                .onTapGesture(perform: logoTapped)
                .accessibilityHidden(true)
            Text(verbatim: AppStrings.string("app_name"))
                .font(.itmo(.headlineSmall))
                .multilineTextAlignment(.center)
                .padding(.top, ItmoSpacing.section)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("auth.title")
            if state.reauthenticationRequired {
                Text(verbatim: AppStrings.string("auth_reauthentication_description"))
                    .font(.itmo(.bodyLarge))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, ItmoSpacing.compact)
                    .accessibilityIdentifier("auth.reauthentication")
            }
            features
                .padding(.top, ItmoSpacing.section)
            if let error = state.error {
                Text(verbatim: error.resolved)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(ItmoColor.error)
                    .multilineTextAlignment(.center)
                    .padding(.top, ItmoSpacing.group)
                    .accessibilityIdentifier("auth.error")
            }
            ItmoProgressButton(title: AppStrings.string("auth_login_itmo_id"), symbol: .login, action: signIn)
                .disabled(!signInEnabled)
                .padding(.top, ItmoSpacing.statePadding)
                .accessibilityIdentifier("auth.signInItmoId")
            Button(action: signInWithRefreshToken) {
                Text(verbatim: AppStrings.string("auth_login_refresh_token"))
                    .font(.itmo(.labelLarge))
                    .multilineTextAlignment(.center)
                    .frame(minHeight: ItmoMetrics.touchTarget)
            }
            .itmoTint()
            .disabled(!signInEnabled)
            .padding(.top, ItmoSpacing.compact)
            .accessibilityIdentifier("auth.signInRefreshToken")
            if state.manualLoginInProgress || state.sessionTransitionInProgress {
                ProgressView()
                    .itmoTint()
                    .padding(.top, ItmoSpacing.compact)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("auth_signing_in")))
                    .accessibilityIdentifier("auth.progress")
            }
            notice(AppStrings.string("auth_official_page_notice"))
                .padding(.top, ItmoSpacing.section)
            notice(AppStrings.string("app_unofficial_notice"))
                .padding(.top, ItmoSpacing.compact)
        }
        .frame(maxWidth: .infinity)
    }

    private var features: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            AuthFeatureRow(symbol: .schedule, title: AppStrings.string("auth_feature_widgets"))
            AuthFeatureRow(symbol: .exercise, title: AppStrings.string("auth_feature_sport"))
            AuthFeatureRow(symbol: .group, title: AppStrings.string("auth_feature_friends"))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func notice(_ text: String) -> some View {
        Text(verbatim: text)
            .font(.itmo(.bodySmall))
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
    }
}

/// `Widget.ItmoWidgets.FeatureRow`: a secondary symbol beside the first line of a `bodyLarge` text.
struct AuthFeatureRow: View {
    let symbol: AppSymbol
    let title: String

    @ScaledMetric(relativeTo: .body) private var symbolWidth = ItmoMetrics.rowSymbolWidth

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
            symbol.image
                .font(.itmo(.bodyLarge))
                .foregroundStyle(.secondary)
                .frame(width: symbolWidth)
                .accessibilityHidden(true)
            Text(verbatim: title)
                .font(.itmo(.bodyLarge))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// The shared `auth_logo`, bundled by path from `:shared:feature-account`'s `composeResources/drawable` (`project.yml`,
/// no copy).
private struct AuthLogo: View {
    private static let image = Bundle.main.path(forResource: "auth_logo", ofType: "webp")
        .flatMap(UIImage.init(contentsOfFile:))

    var body: some View {
        if let image = Self.image {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
        } else {
            Circle().fill(ItmoColor.surfaceContainerHigh)
        }
    }
}
