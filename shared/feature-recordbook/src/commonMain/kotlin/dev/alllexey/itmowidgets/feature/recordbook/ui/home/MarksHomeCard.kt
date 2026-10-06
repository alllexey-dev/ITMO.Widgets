package dev.alllexey.itmowidgets.feature.recordbook.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.markSubjectList
import dev.alllexey.itmowidgets.designsystem.components.cards.ClosableFeedCard
import dev.alllexey.itmowidgets.shared.core.marks_new_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.home_marks_description
import dev.alllexey.itmowidgets.shared.feature.recordbook.home_marks_dismiss
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * The new marks home card: the whole card opens the recordbook, the close button marks the marks read. TalkBack
 * reads `Новые оценки, <count>. <subjects>`.
 */
object MarksHomeCardRenderer : HomeCardRenderer {

    override val kinds: Set<HomeCardKind> = setOf(HomeCardKind.MARKS)

    @Composable
    override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
        check(card is HomeCard.Marks) { "${card.kind} is not the marks card" }
        val title = stringResource(CoreRes.string.marks_new_title)
        val count = card.subjects.size
        val subjects = markSubjectList(card.subjects).asString()
        ClosableFeedCard(
            icon = painterResource(KitRes.drawable.ic_menu_book),
            title = title,
            count = count,
            body = subjects,
            description = stringResource(Res.string.home_marks_description, title, count, subjects),
            dismissLabel = stringResource(Res.string.home_marks_dismiss),
            onOpen = actions.onOpenMarks,
            onDismiss = { actions.onDismiss(HomeCardKind.MARKS) },
            modifier = modifier,
            closeModifier = Modifier.testTag(HomeCardTestTags.DISMISS),
        )
    }
}
