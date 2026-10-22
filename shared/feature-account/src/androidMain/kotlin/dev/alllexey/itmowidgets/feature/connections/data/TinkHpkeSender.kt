package dev.alllexey.itmowidgets.feature.connections.data

import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.util.Bytes
import dev.alllexey.itmowidgets.feature.connections.domain.HpkeSender

/** [HpkeSender] on Tink; `NO_PREFIX` makes its output exactly `enc || ciphertext`, as CryptoKit's. */
class TinkHpkeSender : HpkeSender {

    override fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray {
        val key = HpkePublicKey.create(PARAMETERS, Bytes.copyFrom(recipientPublicKey), null)
        val keyset = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(key).withRandomId().makePrimary())
            .build()
        return keyset.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java).encrypt(plaintext, info)
    }

    companion object {
        init {
            HybridConfig.register()
        }

        val PARAMETERS: HpkeParameters = HpkeParameters.builder()
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.CHACHA20_POLY1305)
            .build()
    }
}
