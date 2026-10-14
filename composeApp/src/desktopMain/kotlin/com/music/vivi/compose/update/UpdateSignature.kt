package com.music.vivi.compose.update

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Ed25519 verification of latest.json using the JDK provider. The public key is a raw 32-byte
 * value, base64 encoded, in the resource [RESOURCE]. A missing or placeholder key fails closed.
 */
object UpdateSignature {
    const val RESOURCE = "/update-public-key.b64"
    private const val ALGORITHM = "Ed25519"
    private const val PUBLIC_KEY_BYTES = 32
    private const val SIGNATURE_BYTES = 64

    /** DER prefix for an Ed25519 SubjectPublicKeyInfo (RFC 8410). */
    private val SPKI_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
    )

    /** The embedded key, or null when the resource is absent, a placeholder or malformed. */
    fun embeddedPublicKey(): PublicKey? {
        val text = UpdateSignature::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return null
        return parsePublicKey(text)
    }

    fun parsePublicKey(base64: String): PublicKey? {
        if (base64.contains("TODO", ignoreCase = true) || base64.contains("PLACEHOLDER", ignoreCase = true)) return null
        val raw = decodeBase64(base64) ?: return null
        if (raw.size != PUBLIC_KEY_BYTES) return null
        return runCatching {
            KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(SPKI_PREFIX + raw))
        }.getOrNull()
    }

    /** True only if [signatureBase64] is a valid Ed25519 signature of exactly [message]. */
    fun verify(message: ByteArray, signatureBase64: String, key: PublicKey): Boolean {
        val signature = decodeBase64(signatureBase64) ?: return false
        if (signature.size != SIGNATURE_BYTES) return false
        return runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(key)
                update(message)
                verify(signature)
            }
        }.getOrDefault(false)
    }

    private fun decodeBase64(text: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(text.filterNot { it.isWhitespace() }) }.getOrNull()
}
