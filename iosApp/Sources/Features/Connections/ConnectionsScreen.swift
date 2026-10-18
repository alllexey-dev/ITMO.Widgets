import Shared
import SwiftUI

/// Android's `ConnectionsScreen` over the shared `ConnectionsViewModel`, the connected devices: the web sessions
/// and agents with access to the account, each with `connections_revoke` behind a confirmation that says what ends at once
/// and what does not. Opened from the the device connection sheet sheet, the settings and a new-connection push.
struct ConnectionsScreen: View {
    @State private var model = ObservableViewModel(ConnectionsViewModel.self, state: \.uiState)

    var body: some View {
        ConnectionsList(
            state: model.state,
            actions: ConnectionsActions(
                retry: model.viewModel.refresh,
                revoke: { model.viewModel.askRevoke(id: $0.id) },
                confirmRevoke: model.viewModel.revoke,
                dismissRevoke: model.viewModel.dismissRevoke
            )
        )
        .navigationTitle(Text(verbatim: AppStrings.string("connections_title")))
        .navigationBarTitleDisplayMode(.inline)
        .observing(model)
        .onChange(of: message) { _, shown in
            guard let shown else { return }
            AccessibilityNotification.Announcement(shown).post()
            model.viewModel.onMessageShown()
        }
        .accessibilityIdentifier("connections.screen")
    }

    /// A revocation's result, announced once as Android's snackbar shows it.
    private var message: String? {
        (model.state as? ConnectionsUiStateContent)?.message?.resolved
    }
}

/// What the list's buttons do; snapshot tests pass no-ops.
struct ConnectionsActions {
    var retry: () -> Void = {}
    var revoke: (ConnectionRow) -> Void = { _ in }
    var confirmRevoke: () -> Void = {}
    var dismissRevoke: () -> Void = {}
}

/// The states without the ViewModel: loading, a failed load, none, or the rows with the revoke confirmation.
struct ConnectionsList: View {
    let state: any ConnectionsUiState
    var actions = ConnectionsActions()

    var body: some View {
        switch onEnum(of: state) {
        case .loading:
            ItmoLoadingView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .error(error):
            ItmoErrorView(title: error.text.resolved, retry: actions.retry)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .content(content) where content.rows.isEmpty:
            ItmoEmptyView(symbol: .computer, title: AppStrings.string("connections_empty"))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .content(content):
            ItmoScreenStack {
                ItmoListGroup(inset: 66) {
                    ForEach(content.rows, id: \.id) { row in
                        ConnectionRowView(row: row, enabled: !content.revoking) { actions.revoke(row) }
                    }
                }
                .padding(.top, ItmoSpacing.compact)
            }
            .confirmationDialog(
                Text(verbatim: content.revoke?.revokeTitle.resolved ?? ""),
                isPresented: Binding(get: { content.revoke != nil }, set: { if !$0 { actions.dismissRevoke() } }),
                titleVisibility: .visible
            ) {
                Button(role: .destructive, action: actions.confirmRevoke) {
                    Text(verbatim: AppStrings.string("connections_revoke"))
                }
                .accessibilityIdentifier("connections.revoke.confirm")
                Button(role: .cancel, action: actions.dismissRevoke) {
                    Text(verbatim: AppStrings.string("common_cancel"))
                }
            } message: {
                Text(verbatim: content.revoke?.revokeText.resolved ?? "")
            }
        }
    }
}

/// Kind icon, name, connected and last active, the shared-session badge, then `connections_revoke`.
struct ConnectionRowView: View {
    let row: ConnectionRow
    let enabled: Bool
    let revoke: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: ItmoSpacing.content) {
            ItmoIconTile(symbol: row.kind == .web ? .computer : .codeBlocks, tone: .primary)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: row.title.resolved)
                    .font(.itmo(.bodyLarge, emphasized: true))
                ForEach([row.connected, row.lastUsed].compactMap { $0?.resolved }, id: \.self) { line in
                    Text(verbatim: line)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
                if row.itmoSession {
                    ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("connections_itmo_badge"), tone: .tertiary))
                        .padding(.top, 4)
                }
                Button(role: .destructive, action: revoke) {
                    Text(verbatim: AppStrings.string("connections_revoke"))
                }
                .buttonStyle(.glass)
                .disabled(!enabled)
                .padding(.top, ItmoSpacing.compact)
                .accessibilityIdentifier("connections.revoke")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ItmoKit.padding)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("connections.row")
    }
}
