package dev.alllexey.itmowidgets.core.presentation

/** Who asked for a refresh, which decides whether it shows progress and whether it may replace one in flight. */
enum class RefreshMode {

    /** The screen refreshes on its own (entry, resume): no indicator, joins a refresh already in flight. */
    Silent,

    /** The user pulled to refresh: shows the indicator and joins a refresh already in flight. */
    Pull,

    /**
     * The user insists on fresh data (a retry button, a manual reload): shows the indicator and replaces a
     * [Silent] or [Pull] refresh in flight. Repositories may read it as "bypass the cache".
     */
    Force;

    val showsIndicator: Boolean get() = this != Silent
}
