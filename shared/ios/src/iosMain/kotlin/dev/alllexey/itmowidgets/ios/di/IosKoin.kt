@file:OptIn(BetaInteropApi::class)

package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.ios.IosPlatform
import kotlin.reflect.KClass
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.ObjCProtocol
import kotlinx.cinterop.getOriginalKotlinClass
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/**
 * Starts the app's Koin graph over [IosKoinModules] with [platform]; the first call of `App.init` after the app
 * locale. A later call keeps the running graph and its platform (the hosted tests run inside the started app) and
 * returns false. Call it on the main thread.
 */
fun startKoinIos(platform: IosPlatform): Boolean {
    if (KoinPlatform.getKoinOrNull() != null) return false
    startKoin {
        allowOverride(false)
        modules(IosKoinModules.all(platform))
    }
    return true
}

/**
 * The app's graph as Swift reads it. Koin's `get` is reified, so Swift names the type by its class or protocol:
 * `IosKoin.shared.get(protocol: AcademicTimeProvider.self) as! AcademicTimeProvider`. Throws Koin's error for a type
 * the graph does not define; reading before [startKoinIos] fails.
 */
object IosKoin {

    val isStarted: Boolean
        get() = KoinPlatform.getKoinOrNull() != null

    /** The definition of the Kotlin class [type]. */
    fun get(type: ObjCClass): Any = koin().get(kotlinClass(type))

    /** The definition of the Kotlin interface [protocol]. */
    fun get(protocol: ObjCProtocol): Any = koin().get(
        requireNotNull(getOriginalKotlinClass(protocol)) { "$protocol is not a Kotlin interface" }
    )

    internal fun koin(): Koin = KoinPlatform.getKoin()
}

/** The Kotlin class behind a class object Swift passes as `X.self`. */
internal fun kotlinClass(type: ObjCClass): KClass<Any> {
    val kClass = requireNotNull(getOriginalKotlinClass(type)) { "$type is not a Kotlin class" }
    @Suppress("UNCHECKED_CAST")
    return kClass as KClass<Any>
}
