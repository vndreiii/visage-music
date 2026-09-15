package com.music.vivi.database

/**
 * SQLDelight's JDBC SQLite driver uses a connection per thread, so writers from
 * the scanner / play-history / UI can hit SQLITE_BUSY. Retry with short backoff.
 */
internal inline fun <T> withSqliteBusyRetry(
    attempts: Int = 8,
    initialDelayMs: Long = 25L,
    block: () -> T,
): T {
    var delayMs = initialDelayMs
    var last: Throwable? = null
    repeat(attempts) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            if (!isSqliteBusy(t) || attempt == attempts - 1) throw t
            last = t
            Thread.sleep(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(500L)
        }
    }
    throw last ?: error("withSqliteBusyRetry exhausted")
}

internal fun isSqliteBusy(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        val msg = cur.message.orEmpty()
        if (msg.contains("SQLITE_BUSY", ignoreCase = true) ||
            msg.contains("database is locked", ignoreCase = true)
        ) {
            return true
        }
        cur = cur.cause
    }
    return false
}
