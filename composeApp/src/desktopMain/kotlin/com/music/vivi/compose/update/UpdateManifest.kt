package com.music.vivi.compose.update

/** A file listed in latest.json. */
data class ManifestAsset(val url: String, val sha256: String, val size: Long)

/**
 * latest.json, published next to the release and signed as latest.json.sig.
 * Schema: { "version", "notes", "pub_date", "assets": { "<name>": { "url", "sha256", "size" } } }
 */
data class UpdateManifest(
    val version: SemVer,
    val notes: String,
    val pubDate: String,
    val assets: Map<String, ManifestAsset>,
) {
    companion object {
        const val MAX_NOTES_CHARS = 4_000
        private val ASSET_NAME = Regex("[A-Za-z0-9._-]{1,128}")
        private val SHA256 = Regex("[a-f0-9]{64}")

        /**
         * Parses and validates. [releaseTag] is the GitHub tag the manifest was fetched from; the
         * version must match it and every asset must be a release download of that same tag.
         */
        fun parse(bytes: ByteArray, releaseTag: String): UpdateManifest {
            val root = MiniJson.parse(bytes.toString(Charsets.UTF_8)).asObject()
            val version = SemVer.parse(root.requiredString("version"))
            require(releaseTag == "v$version") { "Manifest version $version does not match release $releaseTag" }
            val notes = root.string("notes") ?: ""
            require(notes.length <= MAX_NOTES_CHARS) { "Release notes are too long" }
            val pubDate = root.string("pub_date") ?: ""
            val assets = root["assets"].asObject().mapValues { (name, value) ->
                require(ASSET_NAME.matches(name)) { "Invalid asset name" }
                val entry = value.asObject()
                val sha256 = entry.requiredString("sha256")
                require(SHA256.matches(sha256)) { "Invalid sha256 for $name" }
                val size = entry["size"] as? Long ?: throw IllegalArgumentException("Missing size for $name")
                require(size in 1..UpdateLimits.MAX_DOWNLOAD_BYTES) { "Asset size out of range for $name" }
                val url = entry.requiredString("url")
                require(url == UpdateUrls.expectedAssetUrl(releaseTag, name)) { "Asset $name is not from this release" }
                ManifestAsset(url, sha256, size)
            }
            return UpdateManifest(version, notes, pubDate, assets)
        }
    }
}

object UpdateLimits {
    const val MAX_DOWNLOAD_BYTES = 400L * 1024 * 1024
    const val MAX_METADATA_BYTES = 256 * 1024
    const val MAX_SIGNATURE_BYTES = 1024
}

/** Asset naming used by the release workflow: VIsage-<arch>.AppImage. */
object UpdateAssets {
    const val MANIFEST = "latest.json"
    const val SIGNATURE = "latest.json.sig"

    fun appImageName(osArch: String): String? = when (osArch.lowercase()) {
        "amd64", "x86_64" -> "VIsage-x86_64.AppImage"
        "aarch64", "arm64" -> "VIsage-aarch64.AppImage"
        else -> null
    }
}
