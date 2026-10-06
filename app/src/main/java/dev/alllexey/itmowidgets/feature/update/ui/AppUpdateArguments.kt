package dev.alllexey.itmowidgets.feature.update.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateArgs

/** The screen is opened with the result of the check, so it never repeats the request. */
fun AppUpdate.toScreenArguments(): Bundle = AppUpdateArgs.of(this).toBundle()

/** The Fragment host's arguments, under the keys `AppUpdateViewModel` reads from its `SavedStateHandle`. */
fun AppUpdateArgs.toBundle(): Bundle = bundleOf(
    AppUpdateArgs.KEY_INSTALLED_VERSION to installed,
    AppUpdateArgs.KEY_LATEST_VERSION to latest,
    AppUpdateArgs.KEY_NOTE to note,
    AppUpdateArgs.KEY_UNSUPPORTED to unsupported
)
