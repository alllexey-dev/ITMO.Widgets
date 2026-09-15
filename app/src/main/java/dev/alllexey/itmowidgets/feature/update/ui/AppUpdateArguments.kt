package dev.alllexey.itmowidgets.feature.update.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel

/** The screen is opened with the result of the check, so it never repeats the request. */
fun AppUpdate.toScreenArguments(): Bundle = bundleOf(
    AppUpdateViewModel.ARG_INSTALLED_VERSION to installed.raw,
    AppUpdateViewModel.ARG_LATEST_VERSION to latest.raw,
    AppUpdateViewModel.ARG_NOTE to note,
    AppUpdateViewModel.ARG_UNSUPPORTED to unsupported
)
