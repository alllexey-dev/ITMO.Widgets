package dev.alllexey.itmowidgets.testing

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import kotlinx.coroutines.Dispatchers

/** The production binding: instrumented tests exercise the real threads, not a test scheduler. */
val DeviceDispatchers = AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)
