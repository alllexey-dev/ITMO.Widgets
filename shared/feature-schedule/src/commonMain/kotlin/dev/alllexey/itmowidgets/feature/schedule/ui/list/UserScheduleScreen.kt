package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.user_profile_schedule_title
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.user_schedule_title
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** Test tags of [UserScheduleScreen]. */
object UserScheduleTestTags {
    const val TOP_BAR = "user_schedule_top_bar"
}

/**
 * Another user's schedule as a contextual screen (port of `fragment_user_schedule.xml`): the top bar with back and
 * `Расписание: <first name>` (`Расписание` without a [name]), then [content], the schedule itself
 * ([ScheduleRoute] of that user in the app). Stateless.
 */
@Composable
fun UserScheduleScreen(
    name: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val firstName = name?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    Column(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        AppTopBar(
            title = if (firstName == null) {
                stringResource(CoreRes.string.user_profile_schedule_title)
            } else {
                stringResource(Res.string.user_schedule_title, firstName)
            },
            modifier = Modifier.testTag(UserScheduleTestTags.TOP_BAR),
            navigation = { AppTopBarBack(stringResource(CoreRes.string.common_back), onBack) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
