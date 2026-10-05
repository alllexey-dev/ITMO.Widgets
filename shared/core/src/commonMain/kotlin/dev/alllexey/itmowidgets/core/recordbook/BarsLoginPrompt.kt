package dev.alllexey.itmowidgets.core.recordbook

/**
 * The one "sign in to BARS" reminder of the background mark check. A background read that finds the ITMO.ID session
 * ended moves [NONE] to [PENDING]; delivery outside the quiet hours shows it once and moves to [SHOWN]. Any successful
 * BARS answer of the account and switching BARS marks off move it back to [NONE].
 */
enum class BarsLoginPrompt { NONE, PENDING, SHOWN }
