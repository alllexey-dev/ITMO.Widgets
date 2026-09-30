package dev.alllexey.itmowidgets.feature.settings.data

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import javax.inject.Inject

class AndroidBackgroundWorkAccess @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BackgroundWorkAccess {

    override fun isUnrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}
