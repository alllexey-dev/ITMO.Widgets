import Shared
import SwiftUI

/// The sources of one subject's number (DECISIONS.md, the recordbook): every source side by side, the shown one first
/// and marked, why that one, then "got it" and "open the subject". A content-height sheet over the list.
struct RecordbookSourcesSheet: View {
    let sources: RecordbookSources
    let openSubject: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ItmoSheet(title: AppStrings.string("recordbook_sources_title")) {
            Text(verbatim: sources.caption.resolved)
                .font(.itmo(.bodyLarge))
                .foregroundStyle(.secondary)
            ItmoListGroup {
                ForEach(sources.rows, id: \.source) { row in
                    RecordbookSourceRowView(row: row)
                }
            }
            ItmoGroupNote(text: sources.note.resolved)
            VStack(spacing: ItmoSpacing.compact) {
                Button { dismiss() } label: {
                    // onPrimary: the system's white label is unreadable on the dark scheme's light primary.
                    Text(verbatim: AppStrings.string("common_got_it")).foregroundStyle(ItmoColor.onPrimary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
                Button(action: openSubject) {
                    Text(verbatim: AppStrings.string("recordbook_sources_open")).frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .accessibilityIdentifier("recordbook.sources.open")
            }
            .controlSize(.large)
            .itmoTint()
            .padding(.top, ItmoSpacing.related)
        }
        .accessibilityIdentifier("recordbook.sources")
    }
}

private struct RecordbookSourceRowView: View {
    let row: RecordbookSourceRow

    var body: some View {
        HStack(spacing: ItmoSpacing.content) {
            ItmoIconTile(symbol: symbol, tone: row.shown ? .primary : .neutral, size: 30)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: row.title.resolved).font(.itmo(.bodyLarge))
                Text(verbatim: row.note.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                if row.shown {
                    ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("recordbook_sources_shown"), tone: .primary))
                        .padding(.top, 2)
                }
            }
            Spacer(minLength: ItmoSpacing.compact)
            Text(verbatim: row.value ?? AppStrings.string("recordbook_score_pending"))
                .font(.itmo(.titleLarge, emphasized: true))
                .monospacedDigit()
                .foregroundStyle(row.shown ? Color.primary : .secondary)
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
    }

    private var symbol: AppSymbol {
        switch row.source {
        case .bars: .assignment
        case .sheet: .table
        default: .school
        }
    }
}
