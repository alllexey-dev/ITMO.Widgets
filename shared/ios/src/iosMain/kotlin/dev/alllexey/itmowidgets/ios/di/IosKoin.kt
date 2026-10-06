@file:OptIn(BetaInteropApi::class)

package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.ios.IosPlatform
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.ObjCProtocol
import kotlinx.cinterop.getOriginalKotlinClass
import org.koin.core.Koin
import org.koin.core.context.startKoin

/**
 * Starts the app's Koin graph over [IosKoinModules] with [platform]; the first call of `App.init` after the app
 * locale. A later call keeps the running graph and its platform (the hosted tests run inside the started app) and
 * returns false. Call it on the main thread.
 */
fun startKoinIos(platform: IosPlatform): Boolean = IosKoin.start(platform)

/**
 * The app's graph as Swift reads it. Koin's `get` is reified, so Swift names the type by its class or protocol:
 * `IosKoin.shared.get(protocol: AcademicTimeProvider.self) as! AcademicTimeProvider`. Throws Koin's error for a type
 * the graph does not define; reading before [startKoinIos] fails.
 */
object IosKoin {

    // The graph startKoin returned, kept here so iOS code never reads Koin's global context (DiRulesTest), as
    // Android's KoinStarter hands out the graph it started.
    @Volatile
    private var started: Koin? = null

    val isStarted: Boolean
        get() = started != null

    /** The definition of the Kotlin class [type]. */
    fun get(type: ObjCClass): Any = koin().get(kotlinClass(type))

    /** The definition of the Kotlin interface [protocol]. */
    fun get(protocol: ObjCProtocol): Any = koin().get(
        requireNotNull(getOriginalKotlinClass(protocol)) { "$protocol is not a Kotlin interface" }
    )

    internal fun start(platform: IosPlatform): Boolean {
        if (started != null) return false
        started = startKoin {
            allowOverride(false)
            modules(IosKoinModules.all(platform))
        }.koin
        return true
    }

    internal fun koin(): Koin = checkNotNull(started) { "startKoinIos has not run" }
}

/** The Kotlin class behind a class object Swift passes as `X.self`. */
internal fun kotlinClass(type: ObjCClass): KClass<Any> {
    val kClass = requireNotNull(getOriginalKotlinClass(type)) { "$type is not a Kotlin class" }
    @Suppress("UNCHECKED_CAST")
    return kClass as KClass<Any>
}
