package dev.alllexey.itmowidgets.client.sport

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

/**
 * Reads one [subtype] of a sealed sport type through its [base] serializer, so the wire `"type"` is still required
 * and checked, and fails when the answer is another subtype (an `auto` entry from a free-sign route). Writes through
 * [base] as well, keeping `"type"`. Both go through the decoder and encoder, which apply Json's class discriminator;
 * calling [base] directly would expect the array form.
 */
internal class SubtypeSerializer<B : Any, S : B>(
    private val base: KSerializer<B>,
    private val subtype: KClass<S>,
) : KSerializer<S> {

    override val descriptor: SerialDescriptor get() = base.descriptor

    override fun deserialize(decoder: Decoder): S {
        val value = decoder.decodeSerializableValue(base)
        if (!subtype.isInstance(value)) {
            throw SerializationException("Expected ${subtype.simpleName}, got ${value::class.simpleName}")
        }
        @Suppress("UNCHECKED_CAST")
        return value as S
    }

    override fun serialize(encoder: Encoder, value: S) = encoder.encodeSerializableValue(base, value)
}
