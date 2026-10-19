import Shared
import SwiftUI
import UIKit

/// The sign-in screen over the shared `AuthViewModel` (DECISIONS.md, sign-in): the app mark, the name, one line on what
/// the app does and one main path, `auth_login_itmo_id` (`ItmoSignInScreen` above it), with the note that the
/// password goes only to the ITMO.ID page. The refresh token waits under `auth_login_refresh_token` in a sheet. Five taps
/// on the mark open the demo session (`DemoEntryTaps`, ADR 0015); the mark stays decorative for VoiceOver, as on
/// Android, and nothing else names the demo.
struct AuthScreen: View {
    @State private var model = ObservableViewModel(AuthViewModel.self, state: \.uiState)
    @State private var showsSignInPage = false
    @State private var showsOtherWays = false

    var body: some View {
        AuthContent(
            state: model.state,
            logoTapped: { model.viewModel.onLogoTap() },
            signIn: {
                model.viewModel.clearError()
                showsSignInPage = true
            },
            signInWithRefreshToken: { showsOtherWays = true }
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
        .sheet(isPresented: $showsOtherWays) {
            AuthOtherWaysSheet { token in
                showsOtherWays = false
                model.viewModel.signInWithRefreshToken(refreshToken: token)
            }
        }
    }
}

/// The sign-in screen for one `AuthUiState`, without the ViewModel (snapshot tests render it directly).
struct AuthContent: View {
    let state: AuthUiState
    let logoTapped: () -> Void
    let signIn: () -> Void
    let signInWithRefreshToken: () -> Void

    @ScaledMetric(relativeTo: .largeTitle) private var logoSize: CGFloat = 92

    var body: some View {
        Group {
            if state.initializing {
                ItmoLoadingView(title: AppStrings.string("auth_initializing"))
                    .frame(maxHeight: .infinity)
            } else {
                GeometryReader { proxy in
                    ScrollView {
                        content
                            .padding(.horizontal, ItmoSpacing.section)
                            .padding(.vertical, ItmoSpacing.group)
                            .frame(maxWidth: .infinity, minHeight: proxy.size.height)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("auth.screen")
    }

    /// The mark is decorative for VoiceOver. A Debug build under UI tests exposes it, so a test can send its five
    /// taps as one gesture: tapped one by one, the slow CI runner spaced them past `DemoEntryTaps.MAX_INTERVAL`.
    private static var logoFindable: Bool {
        #if DEBUG
        CommandLine.arguments.contains(ITMOWidgetsApp.uiTestsArgument)
        #else
        false
        #endif
    }

    private var signInEnabled: Bool {
        !state.manualLoginInProgress && !state.sessionTransitionInProgress
    }

    private var content: some View {
        VStack(spacing: 0) {
            Spacer(minLength: ItmoSpacing.statePadding)
            AuthLogo()
                .frame(width: logoSize, height: logoSize)
                .padding(20)
                .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 34))
                .contentShape(.rect(cornerRadius: 34))
                .onTapGesture(perform: logoTapped)
                .accessibilityHidden(!Self.logoFindable)
                .accessibilityIdentifier("auth.logo")
            Text(verbatim: AppStrings.string("app_name"))
                .font(.system(.largeTitle, weight: .bold))
                .multilineTextAlignment(.center)
                .padding(.top, 22)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("auth.title")
            Text(verbatim: AppStrings.string(state.reauthenticationRequired ? "auth_reauthentication_description" : "auth_tagline"))
                .font(.itmo(.bodyLarge))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 6)
                .accessibilityIdentifier(state.reauthenticationRequired ? "auth.reauthentication" : "auth.tagline")
            Spacer(minLength: ItmoSpacing.statePadding * 2)
            if let error = state.error {
                Text(verbatim: error.resolved)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(ItmoColor.error)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, ItmoSpacing.content)
                    .accessibilityIdentifier("auth.error")
            }
            Button(action: signIn) {
                Label { Text(verbatim: AppStrings.string("auth_login_itmo_id")) } icon: { AppSymbol.login.image }
                    .font(.itmo(.titleMedium, emphasized: true))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.extraLarge)
            .itmoTint()
            .disabled(!signInEnabled)
            .accessibilityIdentifier("auth.signInItmoId")
            Label { Text(verbatim: AppStrings.string("auth_official_page_notice")) } icon: { AppSymbol.lock.image }
                .font(.itmo(.bodySmall))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 12)
            Button(action: signInWithRefreshToken) {
                Text(verbatim: AppStrings.string("auth_login_refresh_token"))
                    .font(.itmo(.bodyMedium))
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
            Text(verbatim: AppStrings.string("app_unofficial_notice"))
                .font(.itmo(.labelSmall))
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
                .padding(.top, ItmoSpacing.compact)
        }
        .frame(maxWidth: .infinity)
    }
}

/// `auth_login_refresh_token`: why it exists and the refresh token card. The token lives in this sheet's state only and
/// leaves with it; `auth_manual_token_sign_in` stays off while the field is blank.
struct AuthOtherWaysSheet: View {
    let signIn: (String) -> Void
    @State private var token = ""

    var body: some View {
        ItmoSheet(title: AppStrings.string("auth_login_refresh_token")) {
            Text(verbatim: AppStrings.string("auth_other_text"))
                .font(.itmo(.bodyLarge))
                .foregroundStyle(.secondary)
            ItmoCard {
                HStack(spacing: ItmoSpacing.content) {
                    ItmoIconTile(symbol: .key, tone: .primary)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(verbatim: AppStrings.string("auth_manual_token_hint")).font(.itmo(.titleMedium, emphasized: true))
                        Text(verbatim: AppStrings.string("auth_manual_token_note"))
                            .font(.itmo(.bodyMedium))
                            .foregroundStyle(.secondary)
                    }
                }
                HStack {
                    SecureField(AppStrings.string("auth_manual_token_placeholder"), text: $token)
                        .textContentType(.oneTimeCode)
                        .accessibilityIdentifier("auth.refreshToken.field")
                    PasteButton(payloadType: String.self) { strings in
                        if let first = strings.first { token = first }
                    }
                    .labelStyle(.iconOnly)
                    .buttonBorderShape(.circle)
                }
                .padding(.leading, 14)
                .padding(.trailing, 6)
                .padding(.vertical, 6)
                .background(Color(.tertiarySystemGroupedBackground), in: .capsule)
                .padding(.top, ItmoSpacing.compact)
                Button {
                    let value = token
                    token = ""
                    signIn(value)
                } label: {
                    Text(verbatim: AppStrings.string("auth_manual_token_sign_in"))
                        .foregroundStyle(ItmoColor.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .itmoTint()
                .disabled(token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(.top, 4)
                .accessibilityIdentifier("auth.refreshToken.signIn")
            }
        }
        .onDisappear { token = "" }
    }
}

/// The shared `auth_logo`, bundled by path from `:shared:feature-account`'s Android `res/drawable` (`project.yml`,
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
