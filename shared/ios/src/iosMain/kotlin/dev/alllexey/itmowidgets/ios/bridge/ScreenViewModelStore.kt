@file:OptIn(BetaInteropApi::class)

package dev.alllexey.itmowidgets.ios.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alllexey.itmowidgets.ios.di.IosKoin
import dev.alllexey.itmowidgets.ios.di.kotlinClass
import kotlin.reflect.KClass
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCClass
import org.koin.core.parameter.parametersOf

/**
 * The ViewModels of one SwiftUI screen; Swift's `ObservableViewModel` owns one and clears it on deinit. A ViewModel
 * type is created once per store; [clear] runs each ViewModel's `onCleared` and cancels its `viewModelScope`, which
 * nothing else does on iOS. Compose screens never use it: a `ComposeUIViewController` brings its own store.
 */
class ScreenViewModelStore : ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    /** The ViewModel of the Kotlin class [type] (`X.self` in Swift) from the app's Koin graph. */
    fun resolve(type: ObjCClass): ViewModel = resolve(type, emptyList())

    /** As [resolve], with Koin parameters for a definition that takes arguments (`parametersOf`). */
    fun resolve(type: ObjCClass, parameters: List<Any?>): ViewModel {
        val koin = IosKoin.koin()
        val viewModelClass = viewModelClass(type)
        return getOrCreate(type) { koin.get(viewModelClass, null) { parametersOf(*parameters.toTypedArray()) } }
    }

    /** The ViewModel of [type], built by [create] when the store has none: for a ViewModel outside the graph. */
    fun getOrCreate(type: ObjCClass, create: () -> ViewModel): ViewModel {
        val viewModelClass = viewModelClass(type)
        val factory = viewModelFactory { addInitializer(viewModelClass) { create() } }
        return ViewModelProvider.create(viewModelStore, factory)[viewModelClass]
    }

    fun clear() = viewModelStore.clear()

    private fun viewModelClass(type: ObjCClass): KClass<ViewModel> {
        val kClass = kotlinClass(type)
        @Suppress("UNCHECKED_CAST")
        return kClass as KClass<ViewModel>
    }
}
