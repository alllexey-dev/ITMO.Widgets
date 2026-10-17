import Shared
import SwiftUI

/// One person of a social list (DECISIONS.md, Friends): the avatar, the name, the group line and a status line. A pair
/// of actions (an incoming request) sits under the texts, the prominent one first; a single action is a text button at
/// the end (under the texts too at the accessibility sizes); a row without actions opens the card with a chevron. A busy
/// row keeps its buttons inert.
struct PersonRow: View {
    let row: UserRowUi
    var open: (UserRowUi) -> Void = { _ in }
    var act: (UserRowUi, UserAction) -> Void = { _, _ in }

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let actions = [row.primary, row.secondary].compactMap { $0 }
        if actions.count > 1 || (actions.count == 1 && typeSize.isAccessibilitySize) {
            VStack(alignment: .leading, spacing: 0) {
                person(chevron: false)
                ItmoFlow(spacing: ItmoSpacing.compact) {
                    ForEach(actions.indices, id: \.self) { index in
                        let button = actionButton(actions[index])
                        if index == 0 && actions.count > 1 {
                            button.buttonStyle(.glassProminent).foregroundStyle(ItmoColor.onPrimary)
                        } else {
                            button.buttonStyle(.glass)
                        }
                    }
                }
                .itmoTint()
                .padding(.leading, typeSize.isAccessibilitySize ? ItmoKit.padding : ItmoKit.padding + PersonRow.avatar + ItmoSpacing.content)
                .padding(.trailing, ItmoKit.padding)
                .padding(.bottom, 12)
            }
        } else if let action = actions.first {
            HStack(spacing: 0) {
                person(chevron: false)
                actionButton(action)
                    .buttonStyle(.borderless)
                    .itmoTint()
                    .padding(.trailing, ItmoKit.padding)
            }
        } else {
            person(chevron: row.opensProfile)
        }
    }

    /// The avatar and the texts; the whole of it opens the card when the row allows it.
    private func person(chevron: Bool) -> some View {
        let name = row.displayName.resolved
        return ItmoRow(
            title: name,
            subtitle: [row.subtitle.resolved, row.status?.resolved].compactMap { $0 }.joined(separator: "\n"),
            chevron: chevron,
            action: row.opensProfile ? { open(row) } : nil
        ) {
            ItmoAvatar(
                initials: UserNamesKt.personInitials(name: name, familyName: row.familyName, givenName: row.givenName),
                pictureUrl: row.pictureUrl,
                size: PersonRow.avatar
            )
        }
        .accessibilityIdentifier("user_list_row_\(row.isu)")
    }

    private func actionButton(_ action: UserAction) -> some View {
        Button { act(row, action) } label: {
            Text(verbatim: AppStrings.string("user_action_\(action.name.lowercased())")).font(.itmo(.labelLarge, emphasized: true))
        }
        .controlSize(.small)
        .disabled(row.busy)
        .fixedSize()
        .accessibilityIdentifier("user_list_\(action.name.lowercased())_\(row.isu)")
    }

    /// The avatar of a list row; the separators of its group start after it (`ItmoListGroup(inset: 68)`).
    static let avatar: CGFloat = 40
}

/// `rows` as one group under an optional `title` with its `count`; nothing for an empty list.
struct PersonGroup: View {
    let rows: [UserRowUi]
    var title: String?
    var count: String?
    var open: (UserRowUi) -> Void = { _ in }
    var act: (UserRowUi, UserAction) -> Void = { _, _ in }

    var body: some View {
        if !rows.isEmpty {
            if let title { ItmoSectionHeader(title: title, count: count) }
            ItmoListGroup(inset: 68) {
                ForEach(rows, id: \.isu) { PersonRow(row: $0, open: open, act: act) }
            }
        }
    }
}

/// A `UserListItem` list (another user's friends): its headers and the runs of rows between them as groups.
struct PersonList: View {
    let items: [any UserListItem]
    var open: (UserRowUi) -> Void = { _ in }

    var body: some View {
        ForEach(Array(runs.enumerated()), id: \.offset) { _, run in
            PersonGroup(rows: run.rows, title: run.title, open: open)
        }
    }

    private var runs: [(title: String?, rows: [UserRowUi])] {
        var runs: [(title: String?, rows: [UserRowUi])] = [(nil, [])]
        for item in items {
            if let header = item as? UserListItemHeader {
                runs.append((header.title.resolved, []))
            } else if let user = item as? UserListItemUser {
                runs[runs.count - 1].rows.append(user.row)
            }
        }
        return runs
    }
}
