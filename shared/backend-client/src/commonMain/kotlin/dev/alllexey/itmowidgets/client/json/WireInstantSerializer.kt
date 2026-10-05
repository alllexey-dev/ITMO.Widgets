package dev.alllexey.itmowidgets.client.json

import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * A wire date-time with an offset (1.x `OffsetDateTime`). Reads Jackson's form and Gson's FCM form, which drops
 * `:00` seconds (`2026-10-03T12:00+03:00`, SP-02 case 13) and which no built-in parser accepts. Writes
 * `Instant.toString()` (UTC), which 1.x and Backend read.
 */
internal object WireInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.client.WireInstant", PrimitiveKind.STRING)

    private val format = DateTimeComponents.Format {
        date(LocalDate.Formats.ISO)
        char('T')
        hour()
        char(':')
        minute()
        optional {
            char(':')
            second()
            optional {
                char('.')
                secondFraction(1, 9)
            }
        }
        offset(UtcOffset.Formats.ISO)
    }

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            format.parse(text).toInstantUsingOffset()
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Not a wire date-time", error)
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}
