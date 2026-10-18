package dev.alllexey.itmowidgets.feature.settings.presentation


/**
 * What a [SettingsPageProvider] may use of the open screen: its rows, its event queue and the shared update
 * helpers. The ViewModel implements it and keeps the state behind it, which outlives any single page's rows.
 */
interface SettingsPageScope {

    /** The rows on screen now; a dialog checks its row before it acts, since it can outlive that row's state. */
    val currentItems: List<SettingItem>

    /** Sends [event] as the calling action's own effect, before the action returns. */
    fun send(event: SettingsEvent)

    /** Runs [block] in the screen's scope; [block] may wait for its events to be queued. */
    fun launch(block: suspend () -> Unit)

    suspend fun emit(event: SettingsEvent)

    /** Stores a value; a failure becomes an error event, and widgets redraw after a successful store if asked. */
    fun updateLocalSetting(refreshWidgets: Boolean = false, action: suspend () -> Unit)

    /** [updateLocalSetting] for a value a widget draws. */
    fun updateWidgetSetting(action: suspend () -> Unit) = updateLocalSetting(refreshWidgets = true, action = action)
}
