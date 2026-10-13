import SwiftUI

// Rows of a screen's column: a group of rows on one inset grouped surface between cards. Settings pages keep the
// native `Form` rows of ItmoFormRows.swift.

/// Rows on one inset grouped surface with separators between them. `inset` is where separators start: 56 under
/// icon rows, 68 under rows with a 40 pt avatar.
struct ItmoListGroup<Content: View>: View {
    var inset: CGFloat = 56
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) {
            Group(subviews: content) { rows in
                ForEach(rows.indices, id: \.self) { index in
                    rows[index]
                    if index < rows.count - 1 {
                        Divider().padding(.leading, inset)
                    }
                }
            }
        }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: ItmoKit.cardCorner))
    }
}

/// A row: a leading icon tile (or a custom view such as an avatar), title, subtitle, a trailing value, a badge, a
/// custom accessory (a check, a small button), a chevron. `danger` makes the title and icon red. With an `action`
/// the whole row is the button.
struct ItmoRow<Leading: View, Accessory: View>: View {
    let title: String
    var subtitle: String?
    var trailing: String?
    var badge: Int?
    var chevron = false
    var danger = false
    var action: (() -> Void)?
    @ViewBuilder var leading: Leading
    @ViewBuilder var accessory: Accessory

    var body: some View {
        if let action {
            Button(action: action) { label.contentShape(.rect) }
                .buttonStyle(.plain)
        } else {
            label
        }
    }

    private var label: some View {
        HStack(spacing: ItmoSpacing.content) {
            leading
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: title)
                    .font(.itmo(.bodyLarge))
                    .foregroundStyle(danger ? Color.red : Color.primary)
                if let subtitle {
                    Text(verbatim: subtitle)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: ItmoSpacing.compact)
            if let badge, badge > 0 {
                Text(verbatim: "\(badge)")
                    .font(.itmo(.labelMedium, emphasized: true))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(.red, in: .capsule)
            }
            if let trailing {
                Text(verbatim: trailing)
                    .font(.itmo(.bodyLarge))
                    .foregroundStyle(.secondary)
            }
            accessory
            if chevron {
                AppSymbol.chevronRight.image
                    .font(.itmo(.bodySmall, emphasized: true))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
        .accessibilityElement(children: .combine)
    }
}

extension ItmoRow where Leading == ItmoRowIcon?, Accessory == EmptyView {
    /// A row with an optional icon: a tile in `tone`, or a bare symbol in the primary colour.
    init(
        title: String,
        subtitle: String? = nil,
        symbol: AppSymbol? = nil,
        tone: ItmoTone? = nil,
        trailing: String? = nil,
        badge: Int? = nil,
        chevron: Bool = false,
        danger: Bool = false,
        action: (() -> Void)? = nil
    ) {
        self.init(
            title: title, subtitle: subtitle, trailing: trailing, badge: badge, chevron: chevron, danger: danger,
            action: action,
            leading: { symbol.map { ItmoRowIcon(symbol: $0, tone: tone, danger: danger) } },
            accessory: { EmptyView() }
        )
    }
}

extension ItmoRow where Leading == ItmoRowIcon?, Accessory == EmptyView {
    /// The same row with a trailing view after the value: `row.accessory { AppSymbol.check.image }`.
    func accessory<A: View>(@ViewBuilder _ view: () -> A) -> ItmoRow<ItmoRowIcon?, A> {
        ItmoRow<ItmoRowIcon?, A>(
            title: title, subtitle: subtitle, trailing: trailing, badge: badge, chevron: chevron, danger: danger,
            action: action, leading: { leading }, accessory: view
        )
    }
}

extension ItmoRow where Accessory == EmptyView {
    /// A row with a custom leading view, an avatar for a person: `ItmoRow(title: name) { ItmoAvatar(...) }`.
    init(
        title: String,
        subtitle: String? = nil,
        trailing: String? = nil,
        chevron: Bool = false,
        action: (() -> Void)? = nil,
        @ViewBuilder leading: () -> Leading
    ) {
        self.init(
            title: title, subtitle: subtitle, trailing: trailing, chevron: chevron, action: action,
            leading: leading, accessory: { EmptyView() }
        )
    }
}

/// The leading icon of a row: a 30 pt tile in a tone, or a bare symbol in the primary colour.
struct ItmoRowIcon: View {
    let symbol: AppSymbol
    var tone: ItmoTone?
    var danger = false

    var body: some View {
        if let tone {
            ItmoIconTile(symbol: symbol, tone: danger ? .bad : tone, size: 30)
        } else {
            symbol.image
                .foregroundStyle(danger ? Color.red : ItmoColor.primary)
                .frame(width: 30)
                .accessibilityHidden(true)
        }
    }
}

/// A rounded square with a white symbol on a tone.
struct ItmoIconTile: View {
    let symbol: AppSymbol
    let tone: ItmoTone
    var size: CGFloat = 40

    var body: some View {
        symbol.image
            .font(.system(size: size * 0.48, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(tone.fill, in: .rect(cornerRadius: size * 0.26))
            .accessibilityHidden(true)
    }
}

/// A caption under a group or a card, shared by its rows.
struct ItmoGroupNote: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(.itmo(.bodySmall))
            .foregroundStyle(.secondary)
            .padding(.horizontal, ItmoKit.padding)
            .padding(.top, -2)
    }
}
