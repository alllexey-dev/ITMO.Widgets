package dev.alllexey.itmowidgets.core.ui.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents

/**
 * Intents into the main screen for code that must not import the activity.
 *
 * The component equals the one of `Intent(context, MainActivity::class.java)`, so
 * pending intents built here still `filterEquals` the ones placed widgets, the
 * tile and posted notifications already hold.
 */
object AppEntryIntentFactory {

    fun open(context: Context, action: String): Intent = Intent(action)
        .setComponent(ComponentName(context.packageName, AppEntryIntents.MAIN_ACTIVITY))
}
