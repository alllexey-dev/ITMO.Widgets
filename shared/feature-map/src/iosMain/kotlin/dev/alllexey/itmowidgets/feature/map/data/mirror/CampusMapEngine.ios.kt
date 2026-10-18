package dev.alllexey.itmowidgets.feature.map.data.mirror

import dev.alllexey.itmowidgets.core.network.darwinHttpEngine
import io.ktor.client.engine.HttpClientEngine

internal actual fun campusMapEngine(): HttpClientEngine = darwinHttpEngine()
