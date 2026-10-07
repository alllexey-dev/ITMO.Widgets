package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking

/** Signed in directly and through the queue. */
@Preview(name = "signed")
@Composable
private fun SportBookingCardPreview() = ItmoPreview {
    Cards(SportBookingSamples.signed, SportBookingSamples.autoSigned)
}

/** The queue still running: waiting, then notified of a place. */
@Preview(name = "queue")
@Composable
private fun SportBookingCardQueuePreview() = ItmoPreview {
    Cards(SportBookingSamples.queued, SportBookingSamples.notified)
}

/** The queue's outcomes without a booking: it gave up, it signed the user in, it expired. */
@Preview(name = "outcomes")
@Composable
private fun SportBookingCardOutcomesPreview() = ItmoPreview {
    Cards(SportBookingSamples.gaveUp, SportBookingSamples.autoSigned.copy(signed = false), SportBookingSamples.expired)
}

/** Neither signed nor queued, and a predicted lesson in the queue. */
@Preview(name = "not-signed")
@Composable
private fun SportBookingCardNotSignedPreview() = ItmoPreview {
    Cards(SportBookingSamples.notSigned, SportBookingSamples.predicted)
}

/** Long section, teacher, place and friend names and a three-digit queue. */
@Preview(name = "long-names")
@Composable
private fun SportBookingCardLongNamesPreview() = ItmoPreview { Cards(SportBookingSamples.longNames) }

@Composable
private fun Cards(vararg bookings: SportBooking) {
    Column(Modifier.padding(vertical = ItmoTheme.spacing.related)) {
        bookings.forEach { SportBookingCard(it, SportBookingSamples.time, SportBookingActions()) }
    }
}
