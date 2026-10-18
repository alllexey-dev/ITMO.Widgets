import Shared
import SwiftUI

/// Where the points come from (`AppRoutes.SportPoints`) over the shared `SportPointsViewModel`, pushed on the sport stack: visits
/// plus the counted bonus make the total in the ring, the bonus counts up to 40, then My ITMO's awards by month.
/// No forecast.
struct SportPointsScreen: View {
    @State private var model = ObservableViewModel(SportPointsViewModel.self, state: \.uiState)

    var body: some View {
        SportPointsContent(
            state: model.state,
            select: { model.viewModel.select(filter: $0) },
            retry: { model.viewModel.refresh(mode: .force) }
        )
        .refreshable {
            await model.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                ($0.uiState.value as? SportPointsUiStateContent)?.refreshing == true
            })
        }
        .itmoDetailHeader(title: AppStrings.string("sport_points_title"), subtitle: AppStrings.string("sport_points_subtitle"))
        .observing(model)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sport.points")
    }
}

/// Where the points come from without the ViewModel.
struct SportPointsContent: View {
    let state: SportPointsUiState
    var select: (SportPointsFilter) -> Void = { _ in }
    var retry: () -> Void = {}

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state) {
            case .loading:
                ItmoLoadingView()
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(failed.error).resolved,
                    retry: retry
                )
            case let .content(content):
                body(of: content)
            }
        }
    }

    @ViewBuilder
    private func body(of content: SportPointsUiStateContent) -> some View {
        let score = content.score
        ItmoCard(tone: score.done ? .ok : .primary, hero: true) {
            HStack(spacing: ItmoSpacing.section) {
                SportScoreRing(score: score, size: 128)
                VStack(alignment: .leading, spacing: ItmoSpacing.content) {
                    ItmoStatusPill(pill: ItmoPill(text: content.status.resolved, tone: score.done ? .ok : .neutral, symbol: score.done ? .check : nil))
                    term("\(score.visits)", content.visitsLabel.resolved, score.done ? ItmoColor.success : ItmoColor.sportScoreAttendance)
                    term("+ \(score.bonus)", AppStrings.string("sport_score_bonus"), score.done ? ItmoColor.success : ItmoColor.sportScoreBonus)
                }
                Spacer(minLength: 0)
            }
        }
        .accessibilityElement(children: .combine)
        ItmoCard {
            HStack {
                Text(verbatim: AppStrings.string("sport_points_cap_title")).font(.itmo(.titleMedium, emphasized: true))
                Spacer()
                Text(verbatim: content.cap.resolved).font(.itmo(.titleMedium, emphasized: true)).monospacedDigit()
                    .foregroundStyle(ItmoColor.tertiary)
            }
            ItmoProgressBar(value: Double(content.capShare), tint: ItmoColor.tertiary)
            Text(verbatim: AppStrings.string("sport_points_cap_text")).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
        }
        .accessibilityIdentifier("sport.points.cap")
        if let done = content.done {
            ItmoCard(tone: .tertiary) {
                HStack(alignment: .top, spacing: ItmoSpacing.content) {
                    AppSymbol.checkCircle.image.font(.itmo(.titleMedium)).foregroundStyle(ItmoColor.tertiary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: done.resolved).font(.itmo(.titleMedium, emphasized: true))
                        Text(verbatim: AppStrings.string("sport_points_done_text")).font(.itmo(.bodyMedium))
                    }
                }
            }
        }
        ItmoSectionHeader(title: AppStrings.string("sport_points_history"))
        ItmoChips(chips: content.chips.map { ItmoChip(label: $0.label.resolved, selected: $0.selected, count: $0.count?.intValue) }) {
            select(content.chips[$0].filter)
        }
        if content.months.isEmpty {
            ItmoEmptyView(symbol: .emojiEvents, title: AppStrings.string("sport_points_empty"))
        }
        ForEach(content.months, id: \.self) { month in
            SportDayLabel(text: month.title.resolved)
            ItmoListGroup {
                ForEach(Array(month.rows.enumerated()), id: \.offset) { _, row in
                    ItmoRow(
                        title: row.title,
                        subtitle: row.subtitle.resolved,
                        symbol: row.bonus ? .emojiEvents : .sprint,
                        tone: row.bonus ? .tertiary : .primary,
                        trailing: row.points
                    )
                }
            }
        }
    }

    private func term(_ value: String, _ label: String, _ color: Color) -> some View {
        HStack(spacing: 10) {
            Circle().fill(color).frame(width: 9, height: 9).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: value).font(.itmo(.titleLarge, emphasized: true)).monospacedDigit()
                Text(verbatim: label).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
            }
        }
    }
}
