package dev.alllexey.itmowidgets.core.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** The `kotlinx.coroutines` defaults, as Android's `CoroutinesModule` binds them; `main` is the main queue. */
fun systemAppDispatchers(): AppDispatchers = AppDispatchers(
    io = Dispatchers.IO,
    default = Dispatchers.Default,
    main = Dispatchers.Main,
)
