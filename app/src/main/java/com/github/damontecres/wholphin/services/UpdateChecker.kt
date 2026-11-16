package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.edit
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.UpdateChecker.Companion.ASSET_NAME
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.util.Version
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

internal data class LunocoUpdateState(
    val release: Release? = null,
    val manual: Boolean = false,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val percent: Int = 0,
    val notice: Int? = null,
)

internal class UpdateSessionPolicy {
    var prompted = false
    fun mayCheck(now: Long, next: Long, manual: Boolean) = manual || now >= next
    fun mayPrompt(manual: Boolean, ready: Boolean, idleMs: Long, resumed: Boolean, windowFocused: Boolean) =
        resumed && windowFocused && (manual || (ready && !prompted && idleMs >= 30_000))
}

/** The existing updater, restricted to Lunoco's official release assets and app-private files. */
@Singleton
class UpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:StandardOkHttpClient client: OkHttpClient,
) {
    companion object {
        // Legacy release-fixture filename helper only. No upstream network feed remains.
        const val ASSET_NAME = "Wholphin"
        const val APK_NAME = "Lunoco.apk"
        val ACTIVE = BuildConfig.UPDATING_ENABLED // inherited customizable feed remains disabled
        val GITHUB_ENABLED = BuildConfig.LUNOCO_GITHUB_UPDATES_ENABLED
        private const val DAY = 86_400_000L
    }
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()
    private val preferences by lazy { context.getSharedPreferences("lunoco_updates", Context.MODE_PRIVATE) }
    @Volatile private var manualRequested = false
    private val mutex = Mutex()
    private val downloadMutex = Mutex()
    @Volatile private var checkingJob: Job? = null
    internal fun cancelCheck() { checkingJob?.cancel() }
    private val mutableState = MutableStateFlow(LunocoUpdateState())
    internal val state = mutableState.asStateFlow()
    internal val session = UpdateSessionPolicy()
    @Volatile internal var homeReady = false
    @Volatile private var lastInput = SystemClock.elapsedRealtime()
    internal fun userInteraction() { lastInput = SystemClock.elapsedRealtime() }
    internal fun idleMs() = SystemClock.elapsedRealtime() - lastInput
    private val directory get() = File(context.cacheDir, "updates").apply { mkdirs() }

    internal fun stableBuild(): Boolean = BuildConfig.LUNOCO_STABLE_RELEASE

    internal fun storeManaged(): Boolean = !GITHUB_ENABLED || runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName) == "com.android.vending"
    }.getOrDefault(false)

    internal suspend fun checkForUpdates(manual: Boolean) {
        if (storeManaged()) {
            if (manual) mutableState.value = LunocoUpdateState(notice = R.string.lunoco_updates_store)
            return
        }
        if (!stableBuild()) {
            if (manual) mutableState.value = LunocoUpdateState(notice = R.string.lunoco_updates_development)
            return
        }
        if (!manual && !session.mayCheck(System.currentTimeMillis(), preferences.getLong("next_check", 0), false)) return
        if (!mutex.tryLock()) {
            if (manual) {
                manualRequested = true
                mutableState.value = mutableState.value.copy(manual = true)
            }
            return
        }
        manualRequested = manual
        checkingJob = currentCoroutineContext().job
        try {
            val now = System.currentTimeMillis()
            if (!session.mayCheck(now, preferences.getLong("next_check", 0), manual)) return
            // Persist attempts, including offline/rate-limited outcomes, before any network work.
            preferences.edit { putLong("next_check", now + DAY) }
            mutableState.value = LunocoUpdateState(checking = true, manual = manual)
            val release = fetchRelease()
            val update = release?.update
            val installed = installedInfo()
            val stable = stableBuild()
            val eligible = update != null && LunocoReleaseProtocol.eligible(update,
                installed.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(installed), stable)
            val showManually = manualRequested || mutableState.value.manual
            mutableState.value = when {
                eligible && (showManually || !session.prompted) -> LunocoUpdateState(release = release, manual = showManually)
                showManually && !stable -> LunocoUpdateState(notice = R.string.lunoco_updates_development)
                showManually -> LunocoUpdateState(notice = R.string.lunoco_updates_current)
                else -> LunocoUpdateState()
            }
        } catch (ex: CancellationException) {
            mutableState.value = LunocoUpdateState()
            throw ex
        } catch (_: Exception) {
            mutableState.value = LunocoUpdateState(notice = if (manualRequested || mutableState.value.manual) R.string.lunoco_updates_failed else null)
        } finally { checkingJob = null; manualRequested = false; mutex.unlock() }
    }

    internal fun presented() { session.prompted = true }
    internal fun dismiss() { mutableState.value = LunocoUpdateState() }
    internal fun consumeNotice() { mutableState.value = mutableState.value.copy(notice = null) }
    fun getInstalledVersion(): Version = Version.fromString(installedInfo().versionName.orEmpty())
    private fun installedInfo(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    // Compatibility entry points always use the fixed official API, never a saved/custom URL.
    suspend fun maybeShowUpdateToast(updateUrl: String, showNegativeToast: Boolean = false) = checkForUpdates(showNegativeToast)
    suspend fun getLatestRelease(updateUrl: String): Release? =
        if (storeManaged() || !stableBuild()) null else fetchRelease()
    suspend fun getRelease(version: Version): Release? = getLatestRelease("")?.takeIf { it.version == version }

    private suspend fun fetchRelease(): Release? = withContext(WholphinDispatchers.IO) {
        val raw = get(LunocoReleaseProtocol.API).use { response ->
            if (response.code == 404) return@withContext null
            if (response.code == 403 || response.code == 429) {
                val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000) ?: 0
                val retry = response.header("Retry-After")?.toLongOrNull()?.times(1000)?.plus(System.currentTimeMillis()) ?: 0
                preferences.edit { putLong("next_check", maxOf(System.currentTimeMillis() + DAY,
                    minOf(maxOf(reset, retry), System.currentTimeMillis() + 7 * DAY))) }
            }
            check(response.isSuccessful)
            boundedText(response, 1_048_576)
        }
        val assets = LunocoReleaseProtocol.assets(raw) ?: return@withContext null
        val manifest = get(assets.manifestUrl).use { check(it.isSuccessful); boundedText(it, 16384) }
        val metadata = LunocoReleaseProtocol.metadata(assets, manifest, context.packageName)
        Release(Version.fromString(metadata.versionName), metadata.apkUrl, null, assets.notes,
            emptyList(), metadata)
    }

    private suspend fun get(url: String): Response {
        var next = url
        repeat(5) {
            check(next == LunocoReleaseProtocol.API || LunocoReleaseProtocol.trustedDownload(next))
            val request = Request.Builder().url(next).header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lunoco").get().build()
            val response = client.newCall(request).awaitResponse()
            if (response.code !in listOf(301, 302, 303, 307, 308)) return response
            val location = response.header("Location")
            val resolved = location?.let { response.request.url.resolve(it)?.toString() }
            response.close()
            check(url != LunocoReleaseProtocol.API && resolved != null &&
                LunocoReleaseProtocol.trustedDownload(resolved))
            next = requireNotNull(resolved)
        }
        error("Too many redirects")
    }

    private fun boundedText(response: Response, max: Int): String {
        val output = ByteArrayOutputStream()
        response.body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                check(output.size() + n <= max); output.write(buffer, 0, n)
            }
        }
        return output.toString("UTF-8")
    }

    internal suspend fun download(release: Release, callback: DownloadCallback? = null): File = withContext(WholphinDispatchers.IO) {
        check(!storeManaged())
        check(downloadMutex.tryLock())
        val target = File(directory, APK_NAME)
        try {
            val update = requireNotNull(release.update)
            require(update.packageName == context.packageName && update.tag == "v" + update.versionName &&
                update.apkUrl == LunocoReleaseProtocol.assetUrl(update.tag, APK_NAME) &&
                update.bytes in 1..LunocoReleaseProtocol.MAX_APK_BYTES && Regex("[a-f0-9]{64}").matches(update.sha256))
            check(LunocoReleaseProtocol.eligible(update, installedInfo().versionName.orEmpty(),
                PackageInfoCompat.getLongVersionCode(installedInfo()), stableBuild()))
            cleanup()
            check(directory.usableSpace > update.bytes + 16 * 1024 * 1024) { "Insufficient storage" }
            mutableState.value = mutableState.value.copy(downloading = true, percent = 0)
            callback?.contentLength(update.bytes)
            get(update.apkUrl).use { response ->
                val cancelBody = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { response.close() }
                }
                try {
                check(response.isSuccessful)
                val size = response.body.contentLength()
                check(size == -1L || size == update.bytes)
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                target.outputStream().use { out ->
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(65536)
                        var percent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            currentCoroutineContext().ensureActive()
                            if (n < 0) break
                            total += n; check(total <= update.bytes)
                            digest.update(buffer, 0, n); out.write(buffer, 0, n)
                            val p = (100 * total / update.bytes).toInt()
                            if (p != percent) {
                                percent = p
                                mutableState.value = mutableState.value.copy(percent = p)
                                callback?.bytesDownloaded(total)
                            }
                        }
                    }
                }
                check(total == update.bytes)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual == update.sha256) { "Release checksum mismatch" }
                } finally { cancelBody.cancel() }
            }
            validateApk(target, update)
            target
        } catch (ex: Exception) {
            target.delete()
            currentCoroutineContext().ensureActive()
            throw ex
        } finally {
            mutableState.value = mutableState.value.copy(downloading = false)
            downloadMutex.unlock()
        }
    }

    internal fun validateApk(file: File, update: LunocoUpdateMetadata) {
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.path, flags))
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        if (Build.VERSION.SDK_INT >= 24) archive.applicationInfo?.let { require(it.minSdkVersion <= Build.VERSION.SDK_INT) }
        fun signers(info: PackageInfo): Set<String> {
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
            }.toSet()
        }
        LunocoReleaseProtocol.validateArchive(update, archive.packageName, archive.versionName,
            PackageInfoCompat.getLongVersionCode(archive), PackageInfoCompat.getLongVersionCode(installed),
            signers(installed), signers(archive))
    }

    internal fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    internal fun installationIntent(file: File): Intent {
        check(!storeManaged() && canInstall())
        return Intent(Intent.ACTION_VIEW).setDataAndType(
            FileProvider.getUriForFile(context, context.packageName + ".provider", file),
            "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    suspend fun installRelease(release: Release, callback: DownloadCallback) {
        val file = download(release, callback)
        withContext(Dispatchers.Main) {
            try { context.startActivity(installationIntent(file).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (ex: Exception) { cleanup(); throw ex }
        }
    }
    fun hasPermissions() = true // app-private cache needs no storage permission
    fun cleanup() { File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() } }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

@Serializable
data class Release(
    val version: Version,
    val downloadUrl: String?,
    val publishedAt: String?,
    val body: String?,
    val notes: List<String>,
    val update: LunocoUpdateMetadata? = null,
) {
    val content =
        "# ${version}\n" +
            (
                (notes.joinToString("\n").takeIf { it.isNotNullOrBlank() } ?: "") +
                    (body ?: "")
            ).replace(
                Regex("https://github.com/\\w*/\\w+/pull/(\\d+)"),
                "#$1",
            )
                // Remove the last line for full changelog since it's just a link
                .replace(Regex("\\*\\*Full Changelog\\*\\*.*"), "")
}

interface DownloadCallback {
    fun contentLength(contentLength: Long)

    fun bytesDownloaded(bytes: Long)
}

suspend fun copyTo(
    input: InputStream,
    out: OutputStream,
    bufferSize: Int = 64 * 1024,
    callback: DownloadCallback,
): Long =
    withContext(WholphinDispatchers.IO) {
        var bytesCopied: Long = 0
        val buffer = ByteArray(bufferSize)
        var bytes = input.read(buffer)
        while (bytes >= 0) {
            out.write(buffer, 0, bytes)
            bytesCopied += bytes
            callback.bytesDownloaded(bytesCopied)
            bytes = input.read(buffer)
        }
        return@withContext bytesCopied
    }

fun getDownloadUrl(
    assets: JsonArray,
    debug: Boolean,
    supportedABIs: List<String> = Build.SUPPORTED_ABIS.toList(),
): String? {
    val abiSuffix = supportedABIs.firstOrNull().let { if (it != null) "-$it" else "" }
    val releaseSuffix = if (debug) "-debug" else "-release"
    val preferredNames =
        buildList {
            add("$ASSET_NAME${releaseSuffix}$abiSuffix.apk")
            add("$ASSET_NAME$releaseSuffix.apk")
            if (!debug) {
                if (abiSuffix.isNotNullOrBlank()) {
                    add("$ASSET_NAME$abiSuffix.apk")
                }
                add("$ASSET_NAME.apk")
            }
        }
    var preferredAsset: JsonObject? = null
    outer@ for (name in preferredNames) {
        for (asset in assets) {
            val assetName =
                asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            if (name == assetName) {
                preferredAsset = asset.jsonObject
                break@outer
            }
        }
    }
    return preferredAsset
        ?.get("browser_download_url")
        ?.jsonPrimitive
        ?.contentOrNull
}
