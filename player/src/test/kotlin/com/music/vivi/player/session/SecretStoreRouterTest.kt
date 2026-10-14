package com.music.vivi.player.session

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretStoreRouterTest {
    private val dir = createTempDirectory("vsk-router").toFile()
    private val logs = mutableListOf<String>()
    private val releases = mutableListOf<CountDownLatch>()
    private var now = 0L

    @AfterTest
    fun cleanup() {
        releases.forEach { it.countDown() } // let abandoned keyring calls finish
        dir.deleteRecursively()
    }

    /** In-memory keyring; [failure] makes every call throw, [hang] makes calls block until released. */
    private class FakeKeyring(override val description: String = "Fake Secret Service") : SecretBackend {
        val data = mutableMapOf<Pair<String, String>, String>()
        @Volatile var failure: SecretBackendException? = null
        @Volatile var hang: CountDownLatch? = null

        private fun check() {
            hang?.await(10, TimeUnit.SECONDS)
            failure?.let { throw it }
        }

        override fun get(service: String, account: String): String? { check(); return data[service to account] }
        override fun put(service: String, account: String, value: String) { check(); data[service to account] = value }
        override fun delete(service: String, account: String) { check(); data.remove(service to account) }
    }

    private fun router(
        factory: () -> SecretBackend,
        timeoutMillis: Long = 2_000,
    ) = SecretStoreRouter(
        primaryFactory = factory,
        file = EncryptedFileBackend(File(dir, "session.vault"), "k".toByteArray()),
        timeoutMillis = timeoutMillis,
        retryAfterMillis = 60_000,
        clock = { now },
        log = { logs += it },
    )

    private fun SecretStoreRouter.put(service: String, account: String, value: String) =
        withBackend({ it.put(service, account, value) }, { it.put(service, account, value) })

    private fun SecretStoreRouter.get(service: String, account: String): String? =
        withBackend({ it.get(service, account) }, { it.get(service, account) })

    @Test
    fun usesTheKeyringWhenItIsAvailable() {
        val keyring = FakeKeyring()
        val router = router({ keyring })
        router.put("visage", "a", "1")
        assertEquals("1", keyring.data["visage" to "a"])
        assertFalse(File(dir, "session.vault").exists())
        assertEquals("Fake Secret Service", router.backendDescription())
    }

    @Test
    fun fallsBackToTheEncryptedFileWhenNoKeyringExists() {
        var factoryCalls = 0
        val router = router({ factoryCalls++; throw SecretBackendException("no service", permanent = true) })
        router.put("visage", "a", "1")
        assertEquals("1", router.get("visage", "a"))
        assertTrue(File(dir, "session.vault").exists())
        assertEquals(1, factoryCalls, "a permanently missing service is probed once")
        assertEquals("Encrypted file (no keyring available) - no service", router.backendDescription())
    }

    @Test
    fun failureIsCachedSoRepeatedReadsDoNotReprobeTheKeyring() {
        val calls = AtomicInteger()
        val router = router({ calls.incrementAndGet(); throw SecretBackendException("locked") })
        repeat(10) { router.get("visage", "a") }
        assertEquals(1, calls.get())
        now += 61_000
        router.get("visage", "a")
        assertEquals(2, calls.get(), "retried after the cache window")
    }

    @Test
    fun aHungKeyringTimesOutAndTheCallFallsBackWithoutBlocking() {
        val keyring = FakeKeyring()
        keyring.hang = CountDownLatch(1).also { releases += it }
        val router = router({ keyring }, timeoutMillis = 200)
        val started = System.nanoTime()
        router.put("visage", "a", "1")
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMillis < 3_000, "returned after ${elapsedMillis}ms")
        assertEquals("1", router.get("visage", "a"), "value landed in the file")
        assertTrue(router.backendDescription().contains("did not answer"))
    }

    @Test
    fun aHungFactoryTimesOut() {
        val release = CountDownLatch(1).also { releases += it }
        val router = router({ release.await(10, TimeUnit.SECONDS); FakeKeyring() }, timeoutMillis = 200)
        assertNull(router.get("visage", "a"))
        assertTrue(router.backendDescription().startsWith("Encrypted file"))
    }

    @Test
    fun entriesWrittenDuringAnOutageMoveIntoTheKeyringWhenItReturns() {
        val keyring = FakeKeyring()
        keyring.data["visage" to "a"] = "stale-from-keyring"
        keyring.failure = SecretBackendException("locked")
        val router = router({ keyring }, timeoutMillis = 500)
        router.put("visage", "a", "written-during-outage")
        router.put("visage", "b", "second")
        assertEquals("written-during-outage", router.get("visage", "a"))

        // The keyring comes back after the retry window.
        keyring.failure = null
        now += 61_000
        assertEquals("written-during-outage", router.get("visage", "a"), "file wins over the stale keyring value")
        assertEquals("second", keyring.data["visage" to "b"])
        assertFalse(File(dir, "session.vault").exists(), "file is deleted once migrated")
        assertEquals("Fake Secret Service", router.backendDescription())
    }

    @Test
    fun aKeyringFailureMidWriteFallsBackToTheFileForTheWholeWrite() {
        val keyring = FakeKeyring()
        val router = router({ keyring })
        router.put("visage", "a", "1")
        keyring.failure = SecretBackendException("gone")
        router.put("visage", "b", "2")
        assertEquals("2", router.get("visage", "b"))
        assertTrue(logs.any { it.contains("gone") })
    }
}
