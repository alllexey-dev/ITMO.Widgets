package dev.alllexey.itmowidgets.feature.settings.data

import android.os.Build
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import javax.inject.Inject

class AndroidQuickSettingsTileAccess @Inject constructor() : QuickSettingsTileAccess {

    override fun canRequestAdd(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
