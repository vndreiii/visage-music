package com.music.vivi.player.session

/**
 * Stores a secret of any size in a keyring that caps the size of one entry. Windows Credential Manager refuses a
 * secret over about 1280 characters (error 1783, "the stub received bad data"), and a signed-in YouTube session is
 * several times that. A long value is split into pieces under `account#0`, `account#1`, ... and the entry itself holds
 * a marker with the piece count; a short value is stored as is, so existing entries keep working.
 */
internal class ChunkedSecrets(
    private val chunkChars: Int,
    private val rawGet: (account: String) -> String?,
    private val rawPut: (account: String, value: String) -> Unit,
    private val rawDelete: (account: String) -> Unit,
) {
    fun get(account: String): String? {
        val head = rawGet(account) ?: return null
        val count = head.takeIf { it.startsWith(MARKER) }?.removePrefix(MARKER)?.toIntOrNull() ?: return head
        val sb = StringBuilder()
        for (i in 0 until count) sb.append(rawGet(piece(account, i)) ?: return null) // a missing piece means the entry is damaged
        return sb.toString()
    }

    fun put(account: String, value: String) {
        val oldCount = pieceCount(rawGet(account))
        if (value.length <= chunkChars) {
            rawPut(account, value)
            for (i in 0 until oldCount) rawDelete(piece(account, i))
            return
        }
        val pieces = value.chunked(chunkChars)
        // Pieces first, marker last: a failure half way leaves the previous value readable.
        pieces.forEachIndexed { i, chunk -> rawPut(piece(account, i), chunk) }
        rawPut(account, "$MARKER${pieces.size}")
        for (i in pieces.size until oldCount) rawDelete(piece(account, i))
    }

    fun delete(account: String) {
        val count = pieceCount(rawGet(account))
        for (i in 0 until count) rawDelete(piece(account, i))
        rawDelete(account)
    }

    private fun pieceCount(head: String?): Int =
        head?.takeIf { it.startsWith(MARKER) }?.removePrefix(MARKER)?.toIntOrNull() ?: 0

    private fun piece(account: String, i: Int) = "$account#$i"

    private companion object {
        const val MARKER = "visage-chunks:"
    }
}
