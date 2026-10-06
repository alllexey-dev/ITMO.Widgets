package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.network.darwinHttpEngine
import io.ktor.client.engine.HttpClientEngine

/** URLSession without cookie storage or cache; `HttpRedirect` follows redirects and `HttpTimeout` sets the limits. */
internal actual fun publicSheetEngine(): HttpClientEngine = darwinHttpEngine()
