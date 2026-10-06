package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore

/** Part-way through the semester: points still missing, both sectors on an open ring. */
@Preview(name = "expanded")
@Composable
private fun SportScoreCardPreview() = ItmoPreview {
    ScoreFrame { SportScoreCard(SportScore(48, 20, emptyList()), animated = false) }
}

/** Above the goal with the bonus over its 40-point limit: the closed ring, `Зачёт` and the raw bonus beside it. */
@Preview(name = "passed")
@Composable
private fun SportScoreCardPassedPreview() = ItmoPreview {
    ScoreFrame { SportScoreCard(SportScore(90, 52, emptyList()), animated = false) }
}

/** No points yet: only the ring's track. */
@Preview(name = "empty")
@Composable
private fun SportScoreCardEmptyPreview() = ItmoPreview {
    ScoreFrame { SportScoreCard(SportScore(0, 0, emptyList()), animated = false) }
}

/** The compact bar the card collapses into over the scrolled list; the card keeps its expanded measurement. */
@Preview(name = "collapsed")
@Composable
private fun SportScoreCardCollapsedPreview() = ItmoPreview {
    ScoreFrame {
        SportScoreCard(
            SportScore(48, 20, emptyList()),
            collapse = SportScoreCollapseState.fixed(collapsed = true),
            animated = false,
        )
    }
}

@Composable
private fun ScoreFrame(content: @Composable () -> Unit) {
    Box(Modifier.padding(ItmoTheme.spacing.screenMargin)) { content() }
}
