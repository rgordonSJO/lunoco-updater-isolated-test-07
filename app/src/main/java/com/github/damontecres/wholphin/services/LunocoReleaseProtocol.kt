package com.github.damontecres.wholphin.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI

@Serializable
data class LunocoUpdateMetadata(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val sha256: String,
    val bytes: Long,
    val apkUrl: String,
    val releaseId: Long,
)

@Serializable
internal data class LunocoReleaseManifest(
    val schema: Int,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sha256: String,
)

internal data class LunocoReleaseAssets(
    val tag: String, val id: Long, val notes: String,
    val apkUrl: String, val manifestUrl: String, val bytes: Long, val apiDigest: String?,
)

/** Only structured assets on this repository are executable input. Notes are display-only text. */
internal object LunocoReleaseProtocol {
    const val API = "https://api.github.com/repos/rgordonSJO/lunoco-updater-isolated-test-07/releases/latest"
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    private val stable = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
    private val digest = Regex("[a-fA-F0-9]{64}")
    private val json = Json { ignoreUnknownKeys = true }
    fun version(value: String): List<Long>? =
        stable.matchEntire(value)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null }

    fun isNewer(candidate: String, installed: String): Boolean {
        val a = version(candidate) ?: return false
        val b = version(installed) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }

    fun assetUrl(tag: String, name: String) =
        "https://github.com/rgordonSJO/lunoco-updater-isolated-test-07/releases/download/$tag/$name"

    fun trustedDownload(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            uri.fragment == null && uri.host in setOf("github.com", "release-assets.githubusercontent.com",
                "objects.githubusercontent.com") &&
            (uri.host != "github.com" || uri.path.startsWith("/rgordonSJO/lunoco-updater-isolated-test-07/releases/download/"))
    }.getOrDefault(false)

    fun assets(raw: String): LunocoReleaseAssets? {
        val root = json.parseToJsonElement(raw).jsonObject
        require(root["draft"]?.jsonPrimitive?.booleanOrNull == false)
        require(root["prerelease"]?.jsonPrimitive?.booleanOrNull == false)
        val tag = root.getValue("tag_name").jsonPrimitive.content
        require(tag.startsWith("v") && version(tag.removePrefix("v")) != null)
        val id = root.getValue("id").jsonPrimitive.long
        require(id > 0)
        val list = root.getValue("assets").jsonArray
        fun asset(name: String): JsonObject = list.map { it.jsonObject }.single {
            it["name"]?.jsonPrimitive?.content == name
        }.also {
            require(it["state"]?.jsonPrimitive?.content == "uploaded")
            require(it["browser_download_url"]?.jsonPrimitive?.content == assetUrl(tag, name))
        }
        val apk = asset("Lunoco.apk")
        val manifest = asset("Lunoco-update.json")
        val size = apk.getValue("size").jsonPrimitive.long
        require(size in 1..MAX_APK_BYTES)
        require(manifest.getValue("size").jsonPrimitive.long in 1..16384)
        val apiDigest = apk["digest"]?.jsonPrimitive?.contentOrNull?.let {
            require(it.startsWith("sha256:") && digest.matches(it.removePrefix("sha256:")))
            it.removePrefix("sha256:").lowercase()
        }
        return LunocoReleaseAssets(tag, id, root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), "").take(4000),
            assetUrl(tag, "Lunoco.apk"), assetUrl(tag, "Lunoco-update.json"), size, apiDigest)
    }

    fun metadata(assets: LunocoReleaseAssets, raw: String, packageName: String): LunocoUpdateMetadata {
        val m = json.decodeFromString<LunocoReleaseManifest>(raw)
        require(m.schema == 1 && m.packageName == packageName)
        require(m.versionName == assets.tag.removePrefix("v") && version(m.versionName) != null)
        require(m.versionCode in 2..2_100_000_000L && digest.matches(m.sha256))
        val checksum = m.sha256.lowercase()
        require(assets.apiDigest == null || assets.apiDigest == checksum)
        return LunocoUpdateMetadata(assets.tag, m.versionName, m.versionCode, m.packageName,
            checksum, assets.bytes, assets.apkUrl, assets.id)
    }

    fun eligible(update: LunocoUpdateMetadata, installedName: String, installedCode: Long, stableBuild: Boolean) =
        stableBuild && update.versionCode > installedCode && isNewer(update.versionName, installedName)

    fun validateArchive(update: LunocoUpdateMetadata, packageName: String, versionName: String?,
        code: Long, installedCode: Long, installedSigners: Set<String>, archiveSigners: Set<String>) {
        require(packageName == update.packageName && versionName == update.versionName)
        require(code == update.versionCode && code > installedCode)
        // Conservative continuity: rotated signers require a separately verified migration.
        require(installedSigners.isNotEmpty() && archiveSigners == installedSigners)
    }
}
