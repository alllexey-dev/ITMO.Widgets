import Shared
import SwiftUI

/// The profile tab's root (docs/features/me.md) over the shared `MeViewModel`: the static root header with
/// share, the identity card, then the groups of rows. Friends are a counter here (their home is Friends), the
/// connection a status; without the connection a gate stands in place of the people rows. Every row opens its key
/// through the router, a key the demo refuses says so; the sign-out asks first. No developer tools row on iOS. Named
/// apart from the exported Kotlin `MeScreen`.
struct MeTabScreen: View {
    let router: AppRouter
    @State private var model = ObservableViewModel(MeViewModel.self, state: \.uiState)
    @State private var messages = SocialMessages()
    @State private var confirmingSignOut = false

    var body: some View {
        let state = model.state
        MeContent(state: state, actions: actions)
            .refreshable { model.viewModel.refresh(mode: .pull) }
            .itmoRootHeader(title: ShellTab.profile.title, subtitle: nil) {
                if state.header.shareable, let isu = state.header.isu {
                    MeShareButton { SocialSharing.profile(name: state.header.name.resolved, isu: isu.int32Value) }
                }
            }
            .alert(Text(verbatim: AppStrings.string("me_sign_out_confirm_title")), isPresented: $confirmingSignOut) {
                Button(role: .destructive) { model.viewModel.signOut() } label: {
                    Text(verbatim: AppStrings.string("me_sign_out"))
                }
                Button(role: .cancel) {} label: { Text(verbatim: AppStrings.string("common_cancel")) }
            } message: {
                Text(verbatim: AppStrings.string("me_sign_out_confirm_message"))
            }
            .socialMessages(messages)
            .observing(model)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("shell.root.me")
    }

    private var actions: MeContentActions {
        MeContentActions(
            open: { route in messages.opened(router.open(route)) },
            openProjectLink: { url in
                if !SocialSharing.openLink(url) { messages.show(AppStrings.string("link_open_failed")) }
            },
            signOut: { if !model.state.signOutInProgress { confirmingSignOut = true } }
        )
    }
}

/// What the tab's rows ask their host to do.
struct MeContentActions {
    var open: (any AppRoute) -> Void = { _ in }
    var openProjectLink: (String) -> Void = { _ in }
    var signOut: () -> Void = {}
}

/// The tab without the ViewModel: the identity card, the people, ITMO.Widgets and app groups and the sign-out.
struct MeContent: View {
    let state: MeUiState
    var actions = MeContentActions()

    var body: some View {
        ItmoScreenStack {
            MeIdentityCard(header: state.header)
            ItmoSectionHeader(title: AppStrings.string("me_group_people"))
            people
            ItmoSectionHeader(title: AppStrings.string("me_group_widgets"))
            service
            ItmoSectionHeader(title: AppStrings.string("me_group_app"))
            app
            ItmoListGroup {
                ItmoRow(title: AppStrings.string("me_sign_out"), symbol: .logout, tone: .bad, danger: true, action: actions.signOut)
                    .accessibilityIdentifier("sign_out_row")
            }
            .padding(.top, ItmoSpacing.content)
        }
        .accessibilityIdentifier("main")
    }

    /// Friends with the requests as a badge, and privacy; without the connection the gate in their place.
    @ViewBuilder private var people: some View {
        if state.friends is MeFriendsSummaryDisabled {
            ItmoGateCard(
                title: AppStrings.string("me_gate_title"),
                reason: AppStrings.string("me_gate_reason"),
                fix: (AppStrings.string("me_gate_fix"), { actions.open(AppRoutes.Settings(page: "SERVICES")) }),
                symbol: .group
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("people_gate")
        } else {
            let requests = Int(state.incomingRequests)
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("me_friends_title"),
                    subtitle: state.friendsSubtitle?.resolved,
                    symbol: .group,
                    tone: .primary,
                    badge: requests,
                    chevron: true,
                    action: { actions.open(AppRoutes.Friends.shared) }
                )
                .accessibilityValue(
                    Text(verbatim: requests > 0 ? AppStrings.string("me_requests_badge_accessibility", [requests]) : "")
                )
                .accessibilityIdentifier("friends_row")
                ItmoRow(
                    title: AppStrings.string("me_privacy_title"),
                    subtitle: AppStrings.string("me_privacy_description"),
                    symbol: .lock,
                    tone: .tertiary,
                    chevron: true,
                    action: { actions.open(AppRoutes.Settings(page: "PRIVACY")) }
                )
                .accessibilityIdentifier("privacy_row")
            }
        }
    }

    /// The connection as a status, the notification and widget pages as links, the web sign-in.
    private var service: some View {
        let connection = AppRoutes.Settings(page: "SERVICES")
        return ItmoListGroup {
            ItmoRow(
                title: AppStrings.string("me_connection_title"),
                subtitle: state.connectionStatus.resolved,
                symbol: state.connected ? .checkCircle : .cloudOff,
                tone: state.connected ? .ok : .neutral,
                chevron: true,
                action: { actions.open(connection) }
            )
            .accessibilityIdentifier("connection_row")
            ItmoRow(
                title: AppStrings.string("me_notifications_title"),
                symbol: .notification,
                tone: .warn,
                chevron: true,
                action: { actions.open(AppRoutes.Settings(page: "NOTIFICATIONS")) }
            )
            .accessibilityIdentifier("notifications_row")
            ItmoRow(
                title: AppStrings.string("me_widgets_title"),
                symbol: .widgets,
                tone: .primary,
                chevron: true,
                action: { actions.open(AppRoutes.Settings(page: "WIDGETS")) }
            )
            .accessibilityIdentifier("widgets_row")
            ItmoRow(
                title: AppStrings.string("me_web_login_title"),
                subtitle: state.webLoginSubtitle.resolved,
                symbol: .computer,
                tone: .neutral,
                chevron: true,
                action: { actions.open(state.connected ? AppRoutes.WebLogin(code: nil) : connection) }
            )
            .accessibilityIdentifier("web_login_row")
        }
    }

    private var app: some View {
        ItmoListGroup {
            external("me_my_itmo", "me_my_itmo_subtitle", .language, id: "my_itmo_row") {
                actions.open(AppRoutes.MyItmoWeb.shared)
            }
            ItmoRow(
                title: AppStrings.string("settings_title"),
                symbol: .settings,
                tone: .neutral,
                chevron: true,
                action: { actions.open(AppRoutes.Settings(page: "ROOT")) }
            )
            .accessibilityIdentifier("settings_row")
            external("me_telegram", "me_telegram_subtitle", .brandTelegram, id: "telegram_row") {
                actions.openProjectLink(ProjectLinks.shared.TELEGRAM_URL)
            }
            external("me_github", "me_github_subtitle", .brandGithub, id: "github_row") {
                actions.openProjectLink(ProjectLinks.shared.GITHUB_URL)
            }
        }
    }

    /// A row that leaves the app: an arrow out instead of the chevron.
    private func external(
        _ title: String, _ subtitle: String, _ symbol: AppSymbol, id: String, action: @escaping () -> Void
    ) -> some View {
        ItmoRow(
            title: AppStrings.string(title),
            subtitle: AppStrings.string(subtitle),
            symbol: symbol,
            tone: .neutral,
            action: action
        )
        .accessory {
            AppSymbol.openInNew.image
                .font(.itmo(.bodySmall, emphasized: true))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .accessibilityIdentifier(id)
    }
}

/// Avatar, the full name, the study group and the ISU.
struct MeIdentityCard: View {
    let header: MeHeader

    var body: some View {
        let name = header.name.resolved
        ItmoCard {
            HStack(spacing: ItmoSpacing.group) {
                ItmoAvatar(
                    initials: header.initials ?? UserNamesKt.personInitials(name: name),
                    pictureUrl: header.pictureUrl,
                    size: 64
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: name)
                        .font(.itmo(.titleLarge, emphasized: true))
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("profile_name")
                    Group {
                        if let group = header.group {
                            Text(verbatim: group.resolved).accessibilityIdentifier("profile_group")
                        }
                        if let isu = header.isu {
                            Text(verbatim: isuText("me_isu", isu.int32Value)).accessibilityIdentifier("profile_meta")
                        }
                    }
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
    }
}

/// Share in the root header: a glass circle like the bell.
struct MeShareButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppSymbol.share.image.frame(width: 28, height: 28)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel(Text(verbatim: AppStrings.string("share_action")))
        .accessibilityIdentifier("profile_share_button")
    }
}
