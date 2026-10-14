package com.music.vivi.compose.update

import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.security.Signature

class UpdateSignatureTest {
    private val manifest = """{"version":"1.2.3"}""".toByteArray()

    private fun sign(message: ByteArray, privateKey: java.security.PrivateKey): String =
        Base64.getEncoder().encodeToString(Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(message)
            sign()
        })

    @Test
    fun roundTripWithGeneratedKeyVerifiesExactBytesOnly() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val raw = pair.public.encoded.takeLast(32).toByteArray()
        val key = UpdateSignature.parsePublicKey(Base64.getEncoder().encodeToString(raw))
        assertNotNull(key)
        val sig = sign(manifest, pair.private)
        assertTrue(UpdateSignature.verify(manifest, sig, key))
        assertFalse(UpdateSignature.verify(manifest + " ".toByteArray(), sig, key), "one changed byte must fail")
        assertFalse(UpdateSignature.verify(manifest, Base64.getEncoder().encodeToString(ByteArray(64)), key))
        assertFalse(UpdateSignature.verify(manifest, "not base64!!", key))
    }

    @Test
    fun signatureFromAnotherKeyIsRejected() {
        val trusted = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val key = UpdateSignature.parsePublicKey(Base64.getEncoder().encodeToString(trusted.public.encoded.takeLast(32).toByteArray()))!!
        assertFalse(UpdateSignature.verify(manifest, sign(manifest, attacker.private), key))
    }

    @Test
    fun placeholderAndMalformedKeysFailClosed() {
        assertNull(UpdateSignature.parsePublicKey("TODO-REPLACE-WITH-BASE64-ED25519-PUBLIC-KEY"))
        assertNull(UpdateSignature.parsePublicKey(""))
        assertNull(UpdateSignature.parsePublicKey(Base64.getEncoder().encodeToString(ByteArray(31))))
        assertNull(UpdateSignature.embeddedPublicKey(), "the shipped placeholder resource must not load as a key")
    }
}
