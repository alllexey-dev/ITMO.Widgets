package dev.alllexey.itmowidgets.feature.schedule.domain.changes

/** Shows the one summary notification of a check; a new digest replaces the previous one. */
interface ScheduleChangeNotifier {
    fun show(digest: ScheduleChangeDigest)
}
