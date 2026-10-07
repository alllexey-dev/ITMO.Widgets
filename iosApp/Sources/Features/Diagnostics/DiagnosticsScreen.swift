import Shared
import SwiftUI

/// The error journal (IO-08a): `DiagnosticsViewModel`'s records of this launch and a Kotlin crash of the previous
/// one (`IosCrashHook`), newest first. Sharing hands the plain-text journal to the share sheet, which also copies it;
/// clearing asks first, as on Android. Nothing leaves the device unless the user shares it.
struct DiagnosticsScreen: View {
    @State private var model = ObservableViewModel(DiagnosticsViewModel.self, state: \.uiState)
    @State private var confirmsClear = false

    var body: some View {
        DiagnosticsList(state: model.state, formatTime: model.viewModel.formatTime(entry:))
            .navigationTitle(Text(verbatim: AppStrings.string("diagnostics_title")))
            .navigationBarTitleDisplayMode(.inline)
            .observing(model)
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    ShareLink(item: model.viewModel.exportText()) {
                        Label {
                            Text(verbatim: AppStrings.string("ios_diagnostics_share"))
                        } icon: {
                            AppSymbol.share.image
                        }
                    }
                    .disabled(isEmpty)
                    .accessibilityIdentifier("diagnostics.share")
                    Button {
                        confirmsClear = true
                    } label: {
                        Label {
                            Text(verbatim: AppStrings.string("diagnostics_clear"))
                        } icon: {
                            AppSymbol.delete.image
                        }
                    }
                    .disabled(isEmpty)
                    .accessibilityIdentifier("diagnostics.clear")
                }
            }
            .confirmationDialog(
                Text(verbatim: AppStrings.string("diagnostics_clear_confirm_title")),
                isPresented: $confirmsClear,
                titleVisibility: .visible
            ) {
                Button(role: .destructive) {
                    model.viewModel.clear()
                } label: {
                    Text(verbatim: AppStrings.string("diagnostics_clear"))
                }
                .accessibilityIdentifier("diagnostics.clear.confirm")
                Button(role: .cancel) {} label: {
                    Text(verbatim: AppStrings.string("common_cancel"))
                }
            } message: {
                Text(verbatim: AppStrings.string("diagnostics_clear_confirm_message"))
            }
            .accessibilityIdentifier("diagnostics.screen")
    }

    private var isEmpty: Bool {
        (model.state as? DiagnosticsUiStateContent)?.entries.isEmpty ?? true
    }
}

/// The journal's states: loading, empty, and the records with their stack traces folded. Stateless apart from the
/// fold, so snapshot tests render it from fixture states.
struct DiagnosticsList: View {
    let state: any DiagnosticsUiState
    let formatTime: (DiagnosticEntry) -> String

    var body: some View {
        switch onEnum(of: state) {
        case .loading:
            ItmoLoadingView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .content(content) where content.entries.isEmpty:
            ItmoEmptyView(
                symbol: .checkCircle,
                title: AppStrings.string("diagnostics_empty_title"),
                description: AppStrings.string("diagnostics_description")
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .content(content):
            List {
                Section {
                    ForEach(Array(content.entries.enumerated()), id: \.offset) { _, entry in
                        DiagnosticsEntryRow(entry: entry, time: formatTime(entry))
                    }
                } footer: {
                    Text(verbatim: AppStrings.string("diagnostics_description"))
                }
            }
        }
    }
}

/// One record: time, level and tag above the message; a record with a stack trace unfolds it on a tap.
struct DiagnosticsEntryRow: View {
    let entry: DiagnosticEntry
    let time: String
    @State private var expanded = false

    var body: some View {
        if let trace = entry.stackTrace, !trace.isEmpty {
            DisclosureGroup(isExpanded: $expanded) {
                Text(verbatim: trace)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            } label: {
                summary
            }
            .itmoTint()
        } else {
            summary
        }
    }

    private var summary: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.related) {
                Text(verbatim: time)
                    .foregroundStyle(.secondary)
                Text(verbatim: levelText)
                    .foregroundStyle(entry.level == .warning ? Color.secondary : ItmoColor.error)
                Text(verbatim: entry.tag)
                    .foregroundStyle(.secondary)
            }
            .font(.itmo(.labelMedium))
            Text(verbatim: entry.message)
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.primary)
        }
        .accessibilityElement(children: .combine)
    }

    private var levelText: String {
        switch entry.level {
        case .warning: AppStrings.string("diagnostics_level_warning")
        case .error: AppStrings.string("diagnostics_level_error")
        case .crash: AppStrings.string("diagnostics_level_crash")
        }
    }
}
