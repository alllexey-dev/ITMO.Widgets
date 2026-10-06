package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.result.AppResult

/**
 * What a [SettingsPageProvider] may use of the open screen: its rows, its event queue and the shared update
 * helpers. The ViewModel implements it and keeps the state behind it, which outlives any single page's rows.
 */
interface SettingsPageScope {

    /** The rows on screen now; a dialog checks its row before it acts, since it can outlive that row's state. */
    val currentItems: List<SettingItem>

    /** True while a privacy choice is being saved to Backend. */
    val privacyUpdateInProgress: Boolean

    /** Sends [event] as the calling action's own effect, before the action returns. */
    fun send(event: SettingsEvent)

    /** Runs [block] in the screen's scope; [block] may wait for its events to be queued. */
    fun launch(block: suspend () -> Unit)

    suspend fun emit(event: SettingsEvent)

    /** Stores a value; a failure becomes an error event, and widgets redraw after a successful store if asked. */
    fun updateLocalSetting(refreshWidgets: Boolean = false, action: suspend () -> Unit)

    /** [updateLocalSetting] for a value a widget draws. */
    fun updateWidgetSetting(action: suspend () -> Unit) = updateLocalSetting(refreshWidgets = true, action = action)

    /**
     * A background check's switch: stores the value and, when it is turned on, asks for notifications if they are
     * off and offers the background work hint once per device.
     */
    fun updateBackgroundCheck(checked: Boolean, store: suspend () -> Unit)

    /** Saves a privacy choice; the privacy rows stay locked until the answer comes. */
    fun updateSharing(action: suspend () -> AppResult<Unit>)

    /** Reloads the privacy settings from Backend, masked for at least the minimum loading time. */
    fun refreshPrivacy()
}
