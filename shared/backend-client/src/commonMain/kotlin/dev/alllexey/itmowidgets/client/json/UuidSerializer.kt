package dev.alllexey.itmowidgets.client.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.uuid.Uuid

/** A UUID as its hex-and-dash string (Jackson's `java.util.UUID` form); lowercase on write. */
internal object UuidSerializer : KSerializer<Uuid> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.client.Uuid", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Uuid {
        val text = decoder.decodeString()
        return try {
            Uuid.parseHexDash(text)
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Not a UUID", error)
        }
    }

    override fun serialize(encoder: Encoder, value: Uuid) = encoder.encodeString(value.toHexDashString())
}
