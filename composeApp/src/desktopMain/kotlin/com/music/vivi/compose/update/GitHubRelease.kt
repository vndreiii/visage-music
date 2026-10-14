package com.music.vivi.compose.update

/** The fields of GitHub's releases/latest response that the updater uses. */
internal data class GitHubRelease(
    val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    private val assets: Map<String, String>,
) {
    /** The asset's download URL, but only if it is exactly this tag's expected location. */
    fun trustedAssetUrl(name: String): String? {
        val url = assets[name] ?: return null
        return url.takeIf { it == UpdateUrls.expectedAssetUrl(tag, name) }
    }

    companion object {
        fun parse(bytes: ByteArray): GitHubRelease {
            val root = MiniJson.parse(bytes.toString(Charsets.UTF_8)).asObject()
            val assets = (root["assets"] as? List<*>).orEmpty().associate { item ->
                val entry = item.asObject()
                entry.requiredString("name") to entry.requiredString("browser_download_url")
            }
            return GitHubRelease(
                tag = root.requiredString("tag_name"),
                draft = root["draft"] == true,
                prerelease = root["prerelease"] == true,
                assets = assets,
            )
        }
    }
}
