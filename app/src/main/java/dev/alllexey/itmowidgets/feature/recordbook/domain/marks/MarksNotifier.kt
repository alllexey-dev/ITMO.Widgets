package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

/** The two notifications of the mark check; a new one replaces the previous one of its kind. */
interface MarksNotifier {
    /** The unread subjects; a tap opens [target] when there is one, otherwise the recordbook. */
    fun showDigest(digest: MarkDigest, target: MarkSubjectTarget?)

    /** The one "sign in to BARS" reminder. */
    fun showBarsPrompt()
}
