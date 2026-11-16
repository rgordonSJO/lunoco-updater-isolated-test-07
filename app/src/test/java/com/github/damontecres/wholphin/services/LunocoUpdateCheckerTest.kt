package com.github.damontecres.wholphin.services

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.R
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LunocoUpdateCheckerTest {
    private lateinit var context: Context
    private lateinit var checker: UpdateChecker
    private val requests = AtomicInteger()
    private var code = 200
    private var timeout = false
    private var hold: CountDownLatch? = null
    private var entered: CountDownLatch? = null
    private var apkBody: (() -> ResponseBody)? = null
    private val bytes = ByteArray(65536) { (it % 251).toByte() }
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private val apkUrl = LunocoReleaseProtocol.assetUrl("v1.1.0", "Lunoco.apk")
    private fun metadata() = LunocoUpdateMetadata("v1.1.0", "1.1.0", 11, context.packageName,
        hash(bytes), bytes.size.toLong(), apkUrl, 10)
    private fun release() = Release(com.github.damontecres.wholphin.util.Version.fromString("1.1.0"),
        apkUrl, null, "Release notes", emptyList(), metadata())
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("lunoco_updates", 0).edit().clear().commit()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName = "1.0.0"; info.versionCode = 10
        shadowOf(context.packageManager).installPackage(info)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            if (timeout) throw SocketTimeoutException("Controlled timeout")
            val request = chain.request()
            val isApi = request.url.toString() == LunocoReleaseProtocol.API
            if (isApi) { entered?.countDown(); check(hold?.await(5, TimeUnit.SECONDS) != false) }
            val body = when {
                isApi -> """{"id":10,"tag_name":"v1.1.0","draft":false,"prerelease":false,"body":"Release notes","assets":[
                    {"name":"Lunoco.apk","state":"uploaded","size":65536,"digest":"sha256:${hash(bytes)}","browser_download_url":"$apkUrl"},
                    {"name":"Lunoco-update.json","state":"uploaded","size":300,"browser_download_url":"${LunocoReleaseProtocol.assetUrl("v1.1.0", "Lunoco-update.json")}"}]}"""
                    .toResponseBody("application/json".toMediaType())
                request.url.encodedPath.endsWith(".json") ->
                    """{"schema":1,"packageName":"${context.packageName}","versionName":"1.1.0","versionCode":11,"sha256":"${hash(bytes)}"}"""
                        .toResponseBody("application/json".toMediaType())
                else -> apkBody?.invoke() ?: bytes.toResponseBody("application/vnd.android.package-archive".toMediaType())
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (isApi) code else 200)
                .message("Controlled response").body(body).build()
        }.build()
        checker = spyk(UpdateChecker(context, client))
        every { checker.stableBuild() } returns true
        every { checker.storeManaged() } returns false
        every { checker.validateApk(any(), any()) } just Runs
        checker.cleanup()
    }
    @After fun cleanup() { hold?.countDown(); checker.cleanup(); Dispatchers.resetMain() }

    @Test fun officialStableReleaseIsFoundWithTwoBoundedMetadataRequests() = runBlocking<Unit> {
        checker.checkForUpdates(true)
        assertEquals("1.1.0", checker.state.value.release?.update?.versionName)
        assertEquals(2, requests.get())
    }
    @Test fun automaticAttemptsAreThrottledAndManualChecksBypassTheInterval() = runBlocking<Unit> {
        checker.checkForUpdates(false); checker.dismiss()
        checker.checkForUpdates(false)
        assertEquals(2, requests.get())
        checker.checkForUpdates(true)
        assertEquals(4, requests.get())
    }
    @Test fun automaticNetworkFailuresAreQuietAndStillThrottled() = runBlocking<Unit> {
        timeout = true
        checker.checkForUpdates(false)
        assertNull(checker.state.value.notice)
        checker.checkForUpdates(false)
        assertEquals(1, requests.get())
    }
    @Test fun manualTimeoutReportsConciseFailure() = runBlocking<Unit> {
        timeout = true; checker.checkForUpdates(true)
        assertEquals(R.string.lunoco_updates_failed, checker.state.value.notice)
        assertNull(checker.state.value.release)
    }
    @Test fun githubRateLimitingDoesNotCauseAnAutomaticRetryLoop() = runBlocking<Unit> {
        code = 429; checker.checkForUpdates(false); checker.checkForUpdates(false)
        assertEquals(1, requests.get())
        assertNull(checker.state.value.notice)
    }
    @Test fun githubApiFailureIsVisibleOnlyForManualCheck() = runBlocking<Unit> {
        code = 500; checker.checkForUpdates(true)
        assertEquals(R.string.lunoco_updates_failed, checker.state.value.notice)
    }
    @Test fun concurrentChecksShareTheSingletonBusyGuard() = runBlocking<Unit> {
        entered = CountDownLatch(1); hold = CountDownLatch(1)
        val first = async(Dispatchers.Default) { checker.checkForUpdates(false) }
        assertTrue(entered!!.await(5, TimeUnit.SECONDS))
        checker.checkForUpdates(true)
        assertEquals(1, requests.get())
        hold!!.countDown(); first.await()
        assertEquals(2, requests.get())
        assertTrue("Manual request must promote the ongoing automatic check", checker.state.value.manual)
    }
    @Test fun laterPreventsAnAutomaticSessionReprompt() = runBlocking<Unit> {
        checker.checkForUpdates(true); checker.presented(); checker.dismiss()
        context.getSharedPreferences("lunoco_updates", 0).edit().putLong("next_check", 0).commit()
        checker.checkForUpdates(false)
        assertNull(checker.state.value.release)
        checker.checkForUpdates(true)
        assertNotNull(checker.state.value.release)
    }
    @Test fun developmentBuildNeverDownloadsOrAdvertisesAnOlderPublicBuild() = runBlocking<Unit> {
        every { checker.stableBuild() } returns false
        checker.checkForUpdates(false)
        assertNull(checker.state.value.release)
        checker.checkForUpdates(true)
        assertEquals(R.string.lunoco_updates_development, checker.state.value.notice)
        assertEquals(0, requests.get())
        assertThrows(IllegalStateException::class.java) { runBlocking { checker.download(release()) } }
    }
    @Test fun playDistributionDoesNotContactGithub() = runBlocking<Unit> {
        every { checker.storeManaged() } returns true
        checker.checkForUpdates(true)
        assertEquals(0, requests.get())
        assertEquals(R.string.lunoco_updates_store, checker.state.value.notice)
    }
    @Test fun downloadVerifiesExpectedDigestAndThenArchiveBeforeReturningPrivateFile() = runBlocking<Unit> {
        val file = checker.download(release())
        assertTrue(file.canonicalPath.startsWith(File(context.cacheDir, "updates").canonicalPath))
        assertArrayEquals(bytes, file.readBytes())
        verify(exactly = 1) { checker.validateApk(file, metadata()) }
    }
    @Test fun checksumMismatchDeletesFileAndNeverCallsArchiveValidation() = runBlocking<Unit> {
        val wrong = release().copy(update = metadata().copy(sha256 = "b".repeat(64)))
        assertThrows(IllegalStateException::class.java) { runBlocking { checker.download(wrong) } }
        assertFalse(File(context.cacheDir, "updates/Lunoco.apk").exists())
        verify(exactly = 0) { checker.validateApk(any(), any()) }
    }
    @Test fun interruptedDownloadAndArchiveFailureBothDeleteTemporaryApk() = runBlocking<Unit> {
        apkBody = { byteArrayOf(1,2,3).toResponseBody() }
        assertThrows(IllegalStateException::class.java) { runBlocking { checker.download(release()) } }
        assertFalse(File(context.cacheDir, "updates/Lunoco.apk").exists())
        apkBody = null
        every { checker.validateApk(any(), any()) } throws IllegalArgumentException("Controlled invalid archive")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { checker.download(release()) } }
        assertFalse(File(context.cacheDir, "updates/Lunoco.apk").exists())
    }
    @Test fun cancellationClosesActiveResponseAndRemovesPartialFile() = runBlocking<Unit> {
        val progress = CompletableDeferred<Unit>()
        val blocked = CountDownLatch(1)
        apkBody = {
            val stream = object : InputStream() {
                var first = true
                override fun read(): Int = error("Buffered reads only")
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (first) { first = false; bytes.copyInto(b, off, 0, 16); return 16 }
                    blocked.await(5, TimeUnit.SECONDS)
                    return -1
                }
                override fun close() { blocked.countDown() }
            }
            object : ResponseBody() {
                override fun contentType() = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                private val buffered = stream.source().buffer()
                override fun source() = buffered
            }
        }
        val job = launch(Dispatchers.Default) {
            checker.download(release(), object : DownloadCallback {
                override fun contentLength(contentLength: Long) {}
                override fun bytesDownloaded(bytes: Long) { progress.complete(Unit) }
            })
        }
        withTimeout(5000) { progress.await(); job.cancelAndJoin() }
        assertFalse(File(context.cacheDir, "updates/Lunoco.apk").exists())
    }
    @Test fun sameAndOlderReleasesReportUpToDateWithoutOfferingInstallation() = runBlocking<Unit> {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        installed.versionName = "1.1.0"; installed.versionCode = 11
        shadowOf(context.packageManager).installPackage(installed)
        checker.checkForUpdates(true)
        assertEquals(R.string.lunoco_updates_current, checker.state.value.notice)
        assertNull(checker.state.value.release)
        installed.versionName = "1.2.0"; installed.versionCode = 12
        shadowOf(context.packageManager).installPackage(installed)
        checker.checkForUpdates(true)
        assertEquals(R.string.lunoco_updates_current, checker.state.value.notice)
        assertNull(checker.state.value.release)
    }
    @Test fun installationHandoffFailureRemovesDownloadedFile() = runBlocking<Unit> {
        every { checker.installationIntent(any()) } throws ActivityNotFoundException("Controlled missing installer")
        assertThrows(ActivityNotFoundException::class.java) {
            runBlocking { checker.installRelease(release(), object : DownloadCallback {
                override fun contentLength(contentLength: Long) {}
                override fun bytesDownloaded(bytes: Long) {}
            }) }
        }
        assertFalse(File(context.cacheDir, "updates/Lunoco.apk").exists())
    }
    @Test fun missingInstallPermissionNeverStartsInstaller() {
        every { checker.canInstall() } returns false
        assertThrows(IllegalStateException::class.java) {
            checker.installationIntent(File(context.cacheDir, "updates/Lunoco.apk"))
        }
    }
    @Test fun cancelingManualMetadataCheckStopsPendingWorkWithoutAnErrorNotice() = runBlocking<Unit> {
        entered = CountDownLatch(1); hold = CountDownLatch(1)
        val job = launch(Dispatchers.Default) { checker.checkForUpdates(true) }
        assertTrue(entered!!.await(5, TimeUnit.SECONDS))
        checker.cancelCheck()
        withTimeout(5000) { job.join() }
        assertFalse(checker.state.value.checking)
        assertNull(checker.state.value.release)
        assertNull(checker.state.value.notice)
        assertEquals(1, requests.get())
        hold!!.countDown()
    }

}
