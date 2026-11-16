package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType

/**
 * A compact, Jellyfin Android TV-inspired home header. It replaces only the large
 * Wholphin discovery header, not the content, navigation, or playback implementation.
 */
@Composable
fun ClassicHomeHeader(
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 32.dp, top = 18.dp, bottom = 12.dp)
                .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.classic_home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onSearch) {
            Text(stringResource(R.string.search))
        }
        Button(onClick = onFavorites) {
            Text(stringResource(R.string.favorites))
        }
    }
}

/**
 * A D-pad focusable media-library tile. The image is optional: libraries without
 * a primary image get a readable icon and name instead of an empty rectangle.
 */
@Composable
fun ClassicLibraryCard(
    library: ServerNavDrawerItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmHome = LocalJellyLuHomeCards.current
    val imageService = LocalImageUrlService.current
    val density = LocalDensity.current
    val imageSize =
        remember(density) {
            with(density) { IntSize(224.dp.roundToPx(), 126.dp.roundToPx()) }
        }
    val imageUrl =
        remember(library.itemId, imageService, imageSize) {
            imageService.getItemImageUrl(
                itemId = library.itemId,
                imageType = ImageType.PRIMARY,
                fillWidth = imageSize.width,
                fillHeight = imageSize.height,
            )
        }
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    val icon =
        when (library.type) {
            CollectionType.MOVIES -> R.string.fa_film
            CollectionType.TVSHOWS -> R.string.fa_tv
            CollectionType.MUSIC -> R.string.fa_music
            CollectionType.PLAYLISTS -> R.string.fa_list_ul
            CollectionType.HOMEVIDEOS -> R.string.fa_video
            else -> R.string.fa_open_folder
        }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(width = 224.dp, height = 126.dp),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        scale = if (calmHome) CardDefaults.scale(focusedScale = 1f) else CardDefaults.scale(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = stringResource(icon),
                    fontFamily = FontAwesome,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Stable contrast for library titles over any custom server artwork.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(66.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = .88f)),
                            ),
                        ),
            )
            Text(
                text = library.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 11.dp),
            )
        }
    }
}
