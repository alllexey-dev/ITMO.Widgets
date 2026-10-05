package dev.alllexey.itmowidgets.client.json

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** `2026-01-05`, as 1.x `LocalDate.toString()`/`parse` and Backend's Jackson. */
internal object IsoLocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.client.IsoLocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = decoder.decodeIso(LocalDate::parse)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
}

/** `08:20`, `08:20:00` or with a fraction on read, as 1.x `LocalTime.parse`; `LocalTime.toString()` on write. */
internal object IsoLocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.client.IsoLocalTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalTime = decoder.decodeIso(LocalTime::parse)

    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
}

private inline fun <T> Decoder.decodeIso(parse: (String) -> T): T {
    val text = decodeString()
    return try {
        parse(text)
    } catch (error: IllegalArgumentException) {
        throw SerializationException("Not an ISO value", error)
    }
}
