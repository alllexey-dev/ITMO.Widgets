package dev.alllexey.itmowidgets.client.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.enums.EnumEntries

/**
 * The enum policy of the client (13 Q7 (b), table in `CONTRACT.md`). Every wire enum names its policy through one of
 * the subclasses below; the wire value is the constant's name. All three accept only a JSON string: `null`, a number
 * or an object fails. The unknown value itself is never put into the exception message.
 */
internal sealed class WireEnumSerializer<E : Enum<E>>(
    private val serialName: String,
    private val entries: EnumEntries<E>,
) : KSerializer<E> {
    final override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.client.$serialName", PrimitiveKind.STRING)

    final override fun deserialize(decoder: Decoder): E {
        val name = decoder.decodeString()
        return entries.firstOrNull { it.name == name && isDecodable(it) } ?: unknown()
    }

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.name)

    /** Whether [value] may come from the wire under its own name. */
    protected open fun isDecodable(value: E): Boolean = true

    /** The value of a name this client does not know, or a [SerializationException]. */
    protected abstract fun unknown(): E

    protected fun failure(reason: String) = SerializationException("$serialName: $reason")
}

/**
 * Unknown fails. For enums that affect authorization or the request shape: `RelationshipState`,
 * `SharingVisibility`, `LinkVisibility`, `FriendshipEvent` and the request-only enums.
 */
internal abstract class StrictEnumSerializer<E : Enum<E>>(serialName: String, entries: EnumEntries<E>) :
    WireEnumSerializer<E>(serialName, entries) {
    final override fun unknown(): E = throw failure("unknown value")
}

/**
 * Unknown decodes to [fallback], which must be the most restrictive constant (`RestrictionCapability.ALL`), so a
 * newer server can never enable an action on this client.
 */
internal abstract class FallbackEnumSerializer<E : Enum<E>>(
    serialName: String,
    entries: EnumEntries<E>,
    private val fallback: E,
) : WireEnumSerializer<E>(serialName, entries) {
    final override fun unknown(): E = fallback
}

/**
 * Display enums: unknown decodes to [unknown], which the server never sends and the client never encodes, so a
 * value added by a newer server shows as unknown instead of failing the whole answer. Encoding [unknown] fails;
 * inside a request that is a `BackendException.Contract`.
 */
internal abstract class UnknownTolerantEnumSerializer<E : Enum<E>>(
    serialName: String,
    entries: EnumEntries<E>,
    private val unknown: E,
) : WireEnumSerializer<E>(serialName, entries) {
    final override fun isDecodable(value: E): Boolean = value != unknown

    final override fun unknown(): E = unknown

    final override fun serialize(encoder: Encoder, value: E) {
        if (value == unknown) throw failure("${unknown.name} is decode-only")
        super.serialize(encoder, value)
    }
}
