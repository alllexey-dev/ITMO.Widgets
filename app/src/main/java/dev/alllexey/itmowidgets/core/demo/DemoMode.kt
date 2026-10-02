package dev.alllexey.itmowidgets.core.demo

import kotlinx.coroutines.flow.Flow

/**
 * Whether the hidden demo session is on.
 *
 * Every class that talks to My ITMO, BARS, Backend or a public web page checks it
 * before the network call: reads answer with the feature's fictional data, writes
 * with `AppError.DemoUnavailable`. Demo data never leaves the device.
 */
interface DemoMode {

    suspend fun isActive(): Boolean

    fun observeActive(): Flow<Boolean>
}
