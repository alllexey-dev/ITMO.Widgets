package dev.alllexey.itmowidgets.feature.settings.data

import android.os.Build
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess

class AndroidQuickSettingsTileAccess : QuickSettingsTileAccess {

    override fun canRequestAdd(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
