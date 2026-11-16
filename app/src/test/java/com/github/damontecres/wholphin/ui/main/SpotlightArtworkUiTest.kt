package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Text
import coil3.ImageLoader
import coil3.asImage
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import com.github.damontecres.wholphin.test.TestActivity
import dagger.hilt.android.testing.*
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpotlightArtworkUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name; packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    @Before fun setUp() { hilt.inject() }

    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
    private fun painter(): Painter = BitmapPainter(bitmap().asImageBitmap())
    private fun success(): SuccessResult = mockk { every { image } returns bitmap().asImage() }
    private val a = BaseItemDto(id = UUID(0, 1), type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, name = "Artwork A")
    private val b = a.copy(id = UUID(0, 2), name = "Artwork B")
    private val c = a.copy(id = UUID(0, 3), name = "Artwork C")
    private val selected = mutableStateOf(a)
    private val scope = mutableStateOf("account-one")
    private val pending = mutableMapOf<String, CompletableDeferred<ImageResult>>()
    private val loader = mockk<ImageLoader>()
    private var frame: SpotlightFrame? = null
    private val requests = mutableListOf<String>()

    private fun renderFrame() {
        listOf(a, b, c).forEach { pending[it.name!!] = CompletableDeferred() }
        coEvery { loader.execute(any()) } coAnswers {
            val name = firstArg<ImageRequest>().data.toString()
            requests += name
            pending.getValue(name).await()
        }
        compose.setContent {
            CompositionLocalProvider(LocalSpotlightImageLoader provides loader) {
                val movie = selected.value
                val request = remember(movie) { ImageRequest.Builder(compose.activity).data(movie.name).size(1600, 900).build() }
                val current by key(scope.value) { rememberSpotlightFrame(movie, request) }
                SideEffect { frame = current }
                Column {
                    Text(current.movie?.name.orEmpty())
                    key(scope.value) {
                        SpotlightArtwork(current.painter, Color.Black, Modifier.size(100.dp).testTag("art"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun cachedMidfadeStaysOpaqueAndDoesNotRecomposeHost() {
        val page = mutableIntStateOf(0)
        var hostBodies = 0
        compose.setContent {
            SideEffect { hostBodies++ }
            val image = remember(page.intValue) { painter() }
            Column {
                Text("Page ${page.intValue}")
                // Exact 09C alpha/container algorithm, with ready local bitmaps and no I/O.
                Crossfade(page.intValue, animationSpec = tween(180), modifier = Modifier.size(100.dp).testTag("old")) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Image(remember(it) { painter() }, null, Modifier.fillMaxSize())
                    }
                }
                SpotlightArtwork(image, Color.Black, Modifier.size(100.dp).testTag("new"))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            page.intValue++
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithText("Page 1").assertExists()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val oldImage = compose.onNodeWithTag("old").captureToImage()
        val newImage = compose.onNodeWithTag("new").captureToImage()
        val folder = java.io.File("build/reports/lunoco-spotlight09d").apply { mkdirs() }
        listOf("midfade-09c-reference.png" to oldImage, "midfade-09d.png" to newImage).forEach { (name, image) ->
            java.io.File(folder, name).outputStream().use {
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val old = oldImage.toPixelMap()[100, 100].red
        val improved = newImage.toPixelMap()[100, 100].red
        println("SPOTLIGHT09D_MIDFADE old=$old improved=$improved")
        assertTrue("09C reference must reproduce the dark dip", old < .97f)
        assertTrue("Ready white artwork must not expose a dark/empty midpoint", improved > .99f)
        val before = hostBodies
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertEquals("Alpha frames must not execute the host body", before, hostBodies)
    }

    @Test fun delayedImageKeepsMatchingMovieAndCancelsStaleCompletion() {
        renderFrame()
        compose.runOnIdle { pending.getValue(a.name!!).complete(success()) }
        compose.waitUntil { frame?.painter != null }
        compose.runOnIdle { selected.value = b }
        compose.waitForIdle()
        assertEquals(a.id, frame?.movie?.id)
        assertNotNull(frame?.painter)
        compose.runOnIdle { selected.value = c }
        compose.waitForIdle()
        compose.runOnIdle { pending.getValue(b.name!!).complete(success()) }
        compose.waitForIdle()
        assertEquals("Cancelled B must never replace visible A", a.id, frame?.movie?.id)
        compose.runOnIdle { pending.getValue(c.name!!).complete(success()) }
        compose.waitUntil { frame?.movie?.id == c.id }
        assertNotNull(frame?.painter)
        assertEquals(listOf(a.name, b.name, c.name), requests)
    }

    @Test fun imageFailureCommitsCorrectMovieWithCharcoalFallback() {
        renderFrame()
        compose.runOnIdle { pending.getValue(a.name!!).complete(success()) }
        compose.waitUntil { frame?.painter != null }
        compose.runOnIdle { selected.value = b }
        compose.waitForIdle()
        val error = mockk<ErrorResult>()
        compose.runOnIdle { pending.getValue(b.name!!).complete(error) }
        compose.waitUntil { frame?.movie?.id == b.id }
        assertNull(frame?.painter)
    }

    @Test fun changingAccountDoesNotExposePreviousAccountsPresentation() {
        renderFrame()
        compose.runOnIdle { pending.getValue(a.name!!).complete(success()) }
        compose.waitUntil { frame?.painter != null }
        compose.runOnIdle { scope.value = "account-two"; selected.value = b }
        compose.waitForIdle()
        assertEquals(b.id, frame?.movie?.id)
        assertNull(frame?.painter)
        assertEquals(0f, compose.onNodeWithTag("art").captureToImage().toPixelMap()[100, 100].red, .001f)
    }

    @Test fun absentArtworkMakesNoRequestAndRapidPagingEndsOnCorrectImage() {
        var image by mutableStateOf<Painter?>(painter())
        compose.setContent {
            val current by rememberSpotlightFrame(a, null)
            SideEffect { frame = current }
            SpotlightArtwork(image, Color.Black, Modifier.size(100.dp).testTag("art"))
        }
        compose.waitForIdle()
        assertEquals(a.id, frame?.movie?.id)
        assertNull(frame?.painter)
        compose.mainClock.autoAdvance = false
        repeat(5) {
            compose.runOnUiThread {
                image = painter()
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(32)
            assertTrue(compose.onNodeWithTag("art").captureToImage().toPixelMap()[100, 100].red > .99f)
        }
        compose.mainClock.advanceTimeBy(250)
        assertTrue(compose.onNodeWithTag("art").captureToImage().toPixelMap()[100, 100].red > .99f)
    }
    @Test fun framingPreservesTopBottomAndBothEdgeSubjects() {
        fun source(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.DKGRAY)
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.RED
            canvas.drawRect(0f, 0f, w.toFloat(), h / 6f, paint)
            paint.color = android.graphics.Color.GREEN
            canvas.drawRect(0f, h * 5f / 6, w.toFloat(), h.toFloat(), paint)
            paint.color = android.graphics.Color.CYAN
            canvas.drawRect(0f, h / 3f, w / 8f, h * 2f / 3, paint)
            paint.color = android.graphics.Color.BLUE
            canvas.drawRect(w * 7f / 8, h / 3f, w.toFloat(), h * 2f / 3, paint)
        }
        val cases = listOf("standard-16x9" to source(320,180), "cinematic-wide" to source(480,200),
            "low-resolution" to source(32,18))
        val shown = mutableStateOf<Painter>(BitmapPainter(cases.first().second.asImageBitmap()))
        compose.setContent {
            SpotlightArtwork(shown.value, Color.Black,
                Modifier.width(960.dp).height(230.dp).testTag("framing"))
        }
        val lost = mutableListOf<String>()
        for ((name, bitmap) in cases) {
            compose.runOnIdle { shown.value = BitmapPainter(bitmap.asImageBitmap()) }
            compose.waitForIdle()
            val image = compose.onNodeWithTag("framing").captureToImage()
            val output = java.io.File("build/reports/lunoco-updates-artwork-perf/artwork-$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val scale = minOf(image.width.toFloat() / bitmap.width, image.height.toFloat() / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = image.width - width
            val top = (image.height - height) / 2
            val pixels = image.toPixelMap()
            fun sample(x: Float, y: Float) = pixels[x.toInt().coerceIn(0,image.width-1), y.toInt().coerceIn(0,image.height-1)]
            val upper = sample(left + width / 2, top + height / 12)
            val lower = sample(left + width / 2, top + height * 11 / 12)
            val leftSubject = sample(left + width / 16, top + height / 2)
            val rightSubject = sample(left + width * 15 / 16, top + height / 2)
            if (!(upper.red > .9f && upper.green < .2f && lower.green > .9f && lower.red < .2f &&
                    leftSubject.green > .9f && leftSubject.blue > .9f && rightSubject.blue > .9f && rightSubject.green < .2f)) lost += name
        }
        println("ARTWORK_EDGE_MARKER_CASES=3;LOST=$lost")
        assertTrue("Source edge subjects/bands must survive without stretching: $lost", lost.isEmpty())
    }

    @Test fun portraitBackdropResponseUsesCharcoalInsteadOfZoomedPoster() {
        renderFrame()
        val portrait = mockk<SuccessResult> {
            every { image } returns Bitmap.createBitmap(90,160,Bitmap.Config.ARGB_8888).asImage()
        }
        compose.runOnIdle { pending.getValue(a.name!!).complete(portrait) }
        compose.waitUntil { frame?.ready == true }
        assertEquals(a.id, frame?.movie?.id)
        assertNull("Portrait-only source must retain movie metadata and safe fallback", frame?.painter)
    }

}
