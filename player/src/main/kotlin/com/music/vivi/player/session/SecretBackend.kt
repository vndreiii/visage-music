package com.music.vivi.player.session

/**
 * A place secrets can be kept. Implementations signal "no such entry" by returning null (or
 * deleting silently), and "this store cannot be used right now" by throwing [SecretBackendException].
 */
interface SecretBackend {
    /** Human-readable name for the Diagnostics screen. */
    val description: String

    fun get(service: String, account: String): String?
    fun put(service: String, account: String, value: String)
    fun delete(service: String, account: String)
}

/**
 * The store cannot be used. [permanent] means retrying soon is pointless (no such service on this
 * system); otherwise the failure is treated as transient (locked, hung, or not yet started).
 */
class SecretBackendException(
    message: String,
    cause: Throwable? = null,
    val permanent: Boolean = false,
) : RuntimeException(message, cause)
