package com.github.damontecres.wholphin.ui.branding

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LunocoBrandingTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun bitmap(id: Int) = requireNotNull(BitmapFactory.decodeResource(
        context.resources, id, BitmapFactory.Options().apply { inScaled = false },
    ))

    @Test fun appSettingsAndAboutUseTheNewPublicIdentity() {
        assertEquals("Lunoco", context.getString(R.string.app_name))
        assertEquals("Lunoco", context.getString(R.string.app_name_long))
        assertEquals("Lunoco", context.getString(R.string.brand_name))
        assertEquals("Lunoco · Settings", context.getString(R.string.jellylu_settings_title, "Settings"))
        assertEquals("Lunoco · version", context.getString(R.string.jellylu_version_summary, "version"))
        // Upstream attribution is retained rather than presented as our app identity.
        assertEquals("Based on Wholphin. Made for Jellyfin.", context.getString(R.string.jellylu_attribution))
    }

    @Test fun localizedPresetsAndUpdateMessagesDoNotRestoreTheOldBrand() {
        val locales = listOf("en", "cs", "da", "de", "es", "et", "fr", "hr", "id",
            "it", "he", "nb", "nl", "nn", "pl", "pt", "ru", "sk", "sl", "sv",
            "th", "tr", "uk", "zh")
        for (language in locales) {
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val localized = context.createConfigurationContext(config)
            assertEquals(language, "Lunoco", localized.getString(R.string.app_name))
            for (id in listOf(R.string.display_preset_default, R.string.display_preset_compact, R.string.updated_toast)) {
                val text = localized.getString(id)
                assertFalse("$language: $text", text.contains("JellyLu") || text.contains("LunaTV"))
            }
        }
    }

    @Test fun wideAndCompactArtworkHaveTheExpectedUndistortedCanvasSizes() {
        val dimensions = listOf(
            R.drawable.jellylu_banner to (320 to 180),
            R.drawable.lunoco_wordmark to (432 to 138),
            R.drawable.jellylu_emblem to (256 to 256),
            R.drawable.jellylu_launcher_foreground to (432 to 432),
        )
        for ((id, size) in dimensions) {
            val image = bitmap(id)
            assertEquals(size.first, image.width)
            assertEquals(size.second, image.height)
        }
        // Transparent margins prevent a rectangular background behind the compact identity.
        for (id in listOf(R.drawable.jellylu_emblem, R.drawable.lunoco_wordmark)) {
            val image = bitmap(id)
            assertEquals(0, image.getPixel(0, 0).ushr(24))
            assertEquals(0, image.getPixel(image.width - 1, image.height - 1).ushr(24))
        }
    }

    @Test fun adaptiveEmblemFitsEveryPixelInsideTheConservativeMaskSafeCircle() {
        val image = bitmap(R.drawable.jellylu_launcher_foreground)
        var visible = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (image.getPixel(x, y).ushr(24) == 0) continue
            visible++
            val dx = x + .5 - 216
            val dy = y + .5 - 216
            assertTrue("Artwork outside the central 66dp safe circle at $x,$y",
                dx * dx + dy * dy <= 132.0 * 132.0)
        }
        assertTrue("The emblem must not be empty", visible > 5000)
    }

    @Test fun existingLauncherAndSplashResourceBindingsAreRetained() {
        val app = context.applicationInfo
        assertEquals(R.string.app_name, app.labelRes)
        assertEquals(R.mipmap.jellylu_launcher, app.icon)
        assertEquals(R.drawable.jellylu_banner, app.banner)
        assertNotNull(context.getDrawable(R.mipmap.jellylu_launcher))
        assertNotNull(context.getDrawable(R.drawable.jellylu_launch_background))
    }
}
