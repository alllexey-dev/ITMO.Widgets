package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardSurface
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardTimeRow

/** Open offers: a scarce lesson (warning bar), a roomy one, and a signed one with friends. */
@Preview(name = "offers")
@Composable
private fun SportLessonCardPreview() = ItmoPreview {
    Cards(SportLessonSamples.scarce, SportLessonSamples.open, SportLessonSamples.signed)
}

/** Full with the auto-sign offer, full and queued, a prediction, and a request in flight. */
@Preview(name = "waiting")
@Composable
private fun SportLessonCardWaitingPreview() = ItmoPreview {
    Cards(SportLessonSamples.full, SportLessonSamples.waiting, SportLessonSamples.predicted, busy = SportLessonSamples.busy)
}

/** No action: a schedule conflict with its mark, and MyITMO's own reason on a section lesson. */
@Preview(name = "restricted")
@Composable
private fun SportLessonCardRestrictedPreview() = ItmoPreview {
    Cards(SportLessonSamples.conflict, SportLessonSamples.restricted)
}

/** Online, an external venue, and a place not yet known without a teacher. */
@Preview(name = "venues")
@Composable
private fun SportLessonCardVenuesPreview() = ItmoPreview {
    Cards(SportLessonSamples.online, SportLessonSamples.external, SportLessonSamples.unknownPlace)
}

/** The time row of every lesson kind: the chip's short name beside the time. */
@Preview(name = "kinds")
@Composable
private fun SportLessonCardKindsPreview() = ItmoPreview {
    SportCardSurface(onClick = {}) {
        SportLessonSamples.everyKind.forEach { lesson ->
            SportCardTimeRow(
                "18:30–20:00",
                lesson.kind,
                Modifier.padding(vertical = ItmoTheme.spacing.related),
            )
        }
    }
}

@Composable
private fun Cards(vararg lessons: SportLesson, busy: SportLesson? = null) {
    Column(Modifier.padding(vertical = ItmoTheme.spacing.related)) {
        lessons.forEach { lesson -> SportLessonCard(lesson, SportLessonSamples.time, SportLessonActions()) }
        busy?.let { lesson -> SportLessonCard(lesson, SportLessonSamples.time, SportLessonActions(), busy = true) }
    }
}
