package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import org.jellyfin.sdk.model.api.BaseItemDto

internal data class SpotlightFrame(val movie: BaseItemDto?, val painter: Painter?, val ready: Boolean = false)

// Null uses the existing shared loader/cache. Tests can supply a controlled loader without network.
internal val LocalSpotlightImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

/**
 * Request only the selected sized image, not the whole lineup. Keep the last complete movie
 * presentation until this request succeeds or fails. Cancellation drops stale completions.
 * The caller keys this state by account/library/day so an old scope is never displayed.
 */
@Composable
internal fun rememberSpotlightFrame(
    movie: BaseItemDto?,
    request: ImageRequest?,
): State<SpotlightFrame> {
    // No request means immediate branded fallback; no coroutine or image-loader work.
    if (request == null) return rememberUpdatedState(SpotlightFrame(movie, null, ready = true))
    val context = LocalContext.current
    val loader = LocalSpotlightImageLoader.current ?: SingletonImageLoader.get(context)
    return produceState(SpotlightFrame(movie, null), movie, request, loader) {
        // Re-entering controls can cancel an unseen pending page. Reuse the complete visible
        // frame, including a resolved fallback, without another cache lookup or network request.
        if (value.ready && value.movie == movie) return@produceState
        val painter = request.let {
            (loader.execute(it) as? SuccessResult)?.image
                // A wrongly tagged portrait is not a cinematic backdrop. Keep the existing fallback.
                ?.takeIf { image -> image.height > 0 && image.width >= image.height }
                ?.asPainter(context)
        }
        value = SpotlightFrame(movie, painter, ready = true)
    }
}

/** Fraction before the height-fitted artwork begins; no bitmap processing or state reads. */
internal fun spotlightArtworkStartFraction(painter: Painter?, viewport: Size): Float {
    val source = painter?.intrinsicSize ?: return 0f
    if (!source.width.isFinite() || !source.height.isFinite() || source.width <= 0f ||
        source.height <= 0f || viewport.width <= 0f || viewport.height <= 0f) return 0f
    val scale = minOf(viewport.width / source.width, viewport.height / source.height)
    return (1f - source.width * scale / viewport.width).coerceIn(0f, 1f)
}

/**
 * Fit the original landscape composition instead of cropping it to the very wide banner.
 * Two image draws at most; only the incoming image fades. Keeping the outgoing image opaque
 * avoids Crossfade's dark midpoint. ModulateAlpha applies alpha to this single image draw
 * instead of requesting a full-banner offscreen compositing buffer.
 * Animation state is read in the graphics layer, never by Home, text or focus controls.
 */
@Composable
internal fun SpotlightArtwork(
    painter: Painter?,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val fallback = remember(background) { ColorPainter(background) }
    val target = painter ?: fallback
    var settled by remember { mutableStateOf(target) }
    val outgoing = settled.takeUnless { it == target }
    // A new target starts transparent on its very first frame, including rapid manual paging.
    val opacity = remember(target) { Animatable(if (outgoing == null) 1f else 0f) }
    LaunchedEffect(target) {
        if (outgoing != null) {
            opacity.animateTo(1f, tween(180))
            settled = target
        }
    }
    Box(modifier.background(background)) {
        outgoing?.let {
            Image(it, null, contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize())
        }
        Image(target, null, contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (outgoing == null) 1f else opacity.value
                // Vector/drawable painters may overlap internally; preserve group alpha for those.
                compositingStrategy = if (target is BitmapPainter || target is ColorPainter)
                    CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
            })
    }
}
