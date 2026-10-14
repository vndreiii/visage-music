package com.music.vivi.compose.update

/** The string storage the choices live in; DesktopPrefs satisfies it through an adapter. */
interface UpdateKeyValues {
    fun getString(key: String): String?
    fun setString(key: String, value: String)
}

/**
 * Remembers what the user chose about a version. Dismiss hides that version for good (a newer
 * release is still offered). Later hides it for 24 hours.
 */
class UpdateChoices(private val store: UpdateKeyValues) {

    fun setAutoCheck(enabled: Boolean) = store.setString(KEY_AUTO_CHECK, enabled.toString())

    fun autoCheck(): Boolean = store.getString(KEY_AUTO_CHECK)?.toBooleanStrictOrNull() ?: true

    fun setLastChecked(epochMillis: Long) = store.setString(KEY_LAST_CHECKED, epochMillis.toString())

    fun lastChecked(): Long = store.getString(KEY_LAST_CHECKED)?.toLongOrNull() ?: 0L

    fun dismiss(version: String) = store.setString(KEY_DISMISSED, version)

    fun remindLater(version: String, now: Long) {
        store.setString(KEY_LATER_VERSION, version)
        store.setString(KEY_LATER_UNTIL, (now + LATER_MS).toString())
    }

    /**
     * How long before [version] may be shown: 0 = now, positive = wait that many ms,
     * null = never (dismissed).
     */
    fun promptDelay(version: String, now: Long): Long? {
        if (store.getString(KEY_DISMISSED) == version) return null
        if (store.getString(KEY_LATER_VERSION) != version) return 0L
        val until = store.getString(KEY_LATER_UNTIL)?.toLongOrNull() ?: 0L
        return (until - now).coerceAtLeast(0L)
    }

    companion object {
        const val LATER_MS = 24L * 60 * 60 * 1000
        private const val KEY_AUTO_CHECK = "update.autoCheck"
        private const val KEY_LAST_CHECKED = "update.lastCheckedAt"
        private const val KEY_DISMISSED = "update.dismissedVersion"
        private const val KEY_LATER_VERSION = "update.laterVersion"
        private const val KEY_LATER_UNTIL = "update.laterUntil"
    }
}
