package dev.alllexey.itmowidgets.feature.connections.domain

import dev.alllexey.itmowidgets.core.connections.PairLink
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json

/** Seals a [PairingBundle] to the client of a [PairLink]: `base64url(enc || ciphertext)`, no padding, for Backend. */
class PairingSealer(private val hpke: HpkeSender) {

    fun sealBundle(link: PairLink, bundle: PairingBundle): String {
        val plaintext = JSON.encodeToString(PairingBundle.serializer(), bundle).encodeToByteArray()
        val info = PairingCrypto.info(bundle.challengeId, bundle.kind, link.secret)
        val sealed = hpke.seal(link.publicKey, info, plaintext)
        check(sealed.size in MIN_SEALED_BYTES..MAX_SEALED_BYTES) { "Sealed bundle of ${sealed.size} bytes" }
        return BASE64.encode(sealed)
    }

    companion object {
        /** Backend's limit on a stored ciphertext. */
        const val MAX_SEALED_BYTES = 16 * 1024

        /** The encapsulated key and the AEAD tag. */
        private const val MIN_SEALED_BYTES = 32 + 16
        private val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        private val JSON = Json { encodeDefaults = true }
    }
}
