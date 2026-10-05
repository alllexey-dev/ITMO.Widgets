package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview

@Preview
@Composable
private fun SkeletonListPreview() = ItmoPreview {
    Skeleton(SkeletonStyle.List)
}

@Preview
@Composable
private fun SkeletonCardsPreview() = ItmoPreview {
    Skeleton(SkeletonStyle.Cards)
}

/** `item_skeleton_cards.xml`: three 140 dp cards in 480 dp. */
@Preview
@Composable
private fun SkeletonCardsTallRowsPreview() = ItmoPreview {
    Skeleton(SkeletonStyle.Cards, rows = 3, rowHeight = 140.dp)
}

/** Bounds shorter than the rows: the rows that do not fit are not drawn. */
@Preview
@Composable
private fun SkeletonListClippedPreview() = ItmoPreview {
    Skeleton(SkeletonStyle.List, Modifier.height(200.dp))
}
