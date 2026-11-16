package com.github.damontecres.wholphin.ui.discovery

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import dagger.hilt.android.testing.*
import io.mockk.*
import org.jellyfin.sdk.model.api.ImageType
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.decode.DataSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class StudioCardLayoutTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    private var previousLoader: ImageLoader? = null
    private var fixtureLoader: ImageLoader? = null
    private val fetched = AtomicBoolean(false)
    @Before fun setup() { hilt.inject() }
    @After fun cleanup() {
        previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        fixtureLoader?.shutdown()
    }

    private fun render(fontScale: Float, artwork: Boolean) {
        val images = mockk<ImageUrlService>(relaxed = true)
        // Deterministic, unbranded geometry fixture. Exercises the real AsyncImage layout,
        // without depending on Robolectric file decoders or adding a production test hook.
        previousLoader = SingletonImageLoader.get(compose.activity)
        val bitmap = Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.LTGRAY)
        }
        fixtureLoader = ImageLoader.Builder(compose.activity).components {
            add(Fetcher.Factory<coil3.Uri> { _, _, _ ->
                Fetcher {
                    fetched.set(true)
                    ImageFetchResult(bitmap.asImage(), false, DataSource.MEMORY)
                }
            })
        }.build().also { SingletonImageLoader.setUnsafe(it) }
        every { images.getItemImageUrl(any<UUID>(), any(), maxWidth = any(), maxHeight = any(), tag = any()) } returns "fixture://studio/logo"
        val id = UUID(4,4)
        val facet = MovieFacet(id,"20th Century Fox Animation", MovieFacetKind.STUDIO,
            if (artwork) MovieExploreImage(id, ImageType.LOGO, "fixture") else null)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale),
                LocalImageUrlService provides images) {
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    Box(Modifier.fillMaxSize().padding(40.dp)) { MovieFacetCard(facet,{}) }
                }
            }
        }
        if (artwork) compose.waitUntil(5_000) { fetched.get() }
        compose.waitForIdle()
    }

    private fun checkSlots() {
        val card = compose.onNodeWithTag("explore-STUDIO:${UUID(4,4)}")
        card.assertWidthIsEqualTo(224.dp).assertHeightIsEqualTo(120.dp)
        val bounds = card.fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("studio-logo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag("studio-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Logo and wrapped label must never overlap", logo.bottom + 4f <= label.top)
        assertTrue(logo.height > 0f && label.bottom <= bounds.bottom && logo.top >= bounds.top)
        assertEquals(bounds.center.x, logo.center.x, .5f)
        card.requestFocus().assertIsFocused()
        assertEquals(bounds, card.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun longStudioNameCannotOverlapContainedLogo() { render(1f,true); checkSlots() }
    @Test fun largeFontRetainsSeparateLogoAndLabelSlotsAndStableFocusGeometry() { render(1.6f,true); checkSlots() }
    @Test fun missingArtworkKeepsReadableFallbackAndExactCardDimensions() {
        render(1f,false)
        compose.onNodeWithTag("studio-logo",useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("20th Century Fox Animation").assertExists()
        compose.onNodeWithTag("explore-STUDIO:${UUID(4,4)}").assertWidthIsEqualTo(224.dp).assertHeightIsEqualTo(120.dp)
    }
}
