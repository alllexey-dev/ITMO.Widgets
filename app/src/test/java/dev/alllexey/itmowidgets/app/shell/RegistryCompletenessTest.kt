package dev.alllexey.itmowidgets.app.shell

import dev.alllexey.itmowidgets.app.shell.entries.shellEntries
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.appRouteSerializersModule
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
import dev.alllexey.itmowidgets.feature.update.navigation.UpdateRoutes
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Compose shell is the default (SH-1c), so no key `MainActivity` can save or restore may fall back to the
 * placeholder: every `AppRoute` subtype of the `SerializersModule` (route map SH-0, the core keys plus the feature
 * registrations `MainActivity` passes to `ShellHost`) has an entry in [shellEntries]. The keys are read from the
 * module itself, so a key added there without an entry fails here.
 */
class RegistryCompletenessTest {

    private val keys: List<AppRoute> = appRouteSerializersModule(*FEATURE_ROUTES).appRouteSamples()

    @Test
    fun theModuleListsTheCoreAndTheFeatureKeys() {
        val classes = keys.map { it::class }

        assertTrue(AppRoutes.QrPass::class in classes)
        assertTrue(AppRoutes.CancelBookingConfirm::class in classes)
        assertTrue(SportRoutes.SportCommonDetails::class in classes)
        assertTrue(UpdateRoutes.AppUpdate::class in classes)
        assertEquals(classes.size, classes.toSet().size)
    }

    @Test
    fun aDebugBuildHasAnEntryForEveryKey() {
        val registry = shellEntries(debugTools = true)

        assertEquals(emptyList<String>(), keys.filterNot(registry::isRegistered).map(::nameOf))
    }

    @Test
    fun aReleaseBuildLacksOnlyTheDebugTools() {
        val registry = shellEntries(debugTools = false)

        assertEquals(listOf(nameOf(AppRoutes.DebugTools)), keys.filterNot(registry::isRegistered).map(::nameOf))
    }

    private fun nameOf(route: AppRoute): String = route::class.simpleName.orEmpty()

    private companion object {
        /** `MainActivity.FEATURE_ROUTES`: the feature modules' key registrations. */
        val FEATURE_ROUTES = arrayOf(UpdateRoutes.registration, SportRoutes.registration)
    }
}

/**
 * One instance of every `AppRoute` subtype registered in this module, built by its serializer from placeholder
 * values: the registry looks a key up by its class, so the values never matter.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun SerializersModule.appRouteSamples(): List<AppRoute> {
    val serializers = mutableListOf<KSerializer<*>>()
    dumpTo(object : SerializersModuleCollector {
        override fun <T : Any> contextual(
            kClass: KClass<T>,
            provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
        ) = Unit

        override fun <Base : Any, Sub : Base> polymorphic(
            baseClass: KClass<Base>,
            actualClass: KClass<Sub>,
            actualSerializer: KSerializer<Sub>,
        ) {
            if (baseClass == AppRoute::class) serializers += actualSerializer
        }

        override fun <Base : Any> polymorphicDefaultSerializer(
            baseClass: KClass<Base>,
            defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
        ) = Unit

        override fun <Base : Any> polymorphicDefaultDeserializer(
            baseClass: KClass<Base>,
            defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
        ) = Unit
    })
    return serializers.map { PlaceholderDecoder().decodeSerializableValue(it) as AppRoute }
}

/** Decodes any class from zeros, empty strings and collections, the first enum constant and null where allowed. */
@OptIn(ExperimentalSerializationApi::class)
private class PlaceholderDecoder : AbstractDecoder() {
    private var index = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (index < descriptor.elementsCount) index++ else CompositeDecoder.DECODE_DONE

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = PlaceholderDecoder()

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = 0

    override fun decodeNotNullMark(): Boolean = false

    override fun decodeBoolean(): Boolean = false

    override fun decodeByte(): Byte = 0

    override fun decodeShort(): Short = 0

    override fun decodeInt(): Int = 0

    override fun decodeLong(): Long = 0

    override fun decodeFloat(): Float = 0f

    override fun decodeDouble(): Double = 0.0

    override fun decodeChar(): Char = ' '

    override fun decodeString(): String = ""

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = 0
}
