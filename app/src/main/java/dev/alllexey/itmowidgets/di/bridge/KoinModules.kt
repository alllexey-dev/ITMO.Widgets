package dev.alllexey.itmowidgets.di.bridge

import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import org.koin.core.module.Module

/**
 * The Koin modules of the release graph, which [KoinStarter] loads and `KoinGraphTest` checks.
 *
 * A lane adds one line per module, and L07 reviews it: a Hilt to Koin `<Feature>Bridge` from this package goes to
 * [bridges], a module whose definitions Koin constructs (`feature-<x>/di`, `core/di`) goes to [constructed]. Debug
 * fixture overrides stay out of both lists.
 */
object KoinModules {

    /** Types Hilt constructs and Koin only forwards; the graph check takes them as given. */
    val bridges: List<Module> = listOf(
        coreBridgeModule,
        resourcesBridgeModule,
        reviewsBridgeModule,
        qrBridgeModule,
    )

    /** Definitions Koin constructs; the graph check verifies their constructors. */
    val constructed: List<Module> = listOf(
        qrModule,
    )

    val all: List<Module> get() = bridges + constructed
}
