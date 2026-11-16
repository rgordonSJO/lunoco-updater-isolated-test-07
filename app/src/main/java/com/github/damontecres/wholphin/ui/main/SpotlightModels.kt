package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlayAccess
import java.util.UUID

internal data class SpotlightScope(val serverId: UUID, val userId: UUID, val libraryId: UUID)
internal data class SpotlightCandidate(val scope: SpotlightScope, val movie: BaseItemDto)
internal data class SpotlightRatings(val critic: Float?, val community: Float?)
internal fun spotlightRatings(movie: BaseItemDto) = SpotlightRatings(
    movie.criticRating?.takeIf { it.isFinite() && it in 0f..100f },
    movie.communityRating?.takeIf { it.isFinite() && it in 0f..10f },
)

internal enum class SpotlightRejection {
    SCOPE, IDENTITY, NON_MOVIE, PLACEHOLDER, EXTRA, VIRTUAL, PLAY_ACCESS_MISSING, PLAY_BLOCKED, NO_MEDIA_SOURCE,
}

internal fun spotlightRejection(candidate: SpotlightCandidate, scope: SpotlightScope): SpotlightRejection? =
    candidate.movie.run {
        when {
            candidate.scope != scope -> SpotlightRejection.SCOPE
            id == UUID(0, 0) || name.isNullOrBlank() -> SpotlightRejection.IDENTITY
            type != BaseItemKind.MOVIE -> SpotlightRejection.NON_MOVIE
            isPlaceHolder == true -> SpotlightRejection.PLACEHOLDER
            extraType != null -> SpotlightRejection.EXTRA
            locationType?.name == "VIRTUAL" -> SpotlightRejection.VIRTUAL
            playAccess == null -> SpotlightRejection.PLAY_ACCESS_MISSING
            playAccess != PlayAccess.FULL -> SpotlightRejection.PLAY_BLOCKED
            // Jellyfin omits MediaSourceCount for the ordinary single-source case.
            // An omitted count is not zero; explicit zero/negative still rejects.
            mediaSourceCount?.let { it <= 0 } == true -> SpotlightRejection.NO_MEDIA_SOURCE
            else -> null
        }
    }

internal fun spotlightEligible(candidate: SpotlightCandidate, scope: SpotlightScope) =
    spotlightRejection(candidate, scope) == null

internal fun spotlightHasBackdrop(movie: BaseItemDto) =
    movie.backdropImageTags.orEmpty().any { it.isNotBlank() }

internal data class SpotlightPoolAudit(
    val fetched: Int,
    val accepted: Int,
    val rejections: Map<SpotlightRejection, Int>,
    val missingBackdrop: Int,
    val omittedSourceCount: Int,
)

internal fun spotlightPoolAudit(candidates: List<SpotlightCandidate>, scope: SpotlightScope): SpotlightPoolAudit {
    val rejections = candidates.mapNotNull { spotlightRejection(it, scope) }.groupingBy { it }.eachCount()
    return SpotlightPoolAudit(
        fetched = candidates.size,
        accepted = candidates.size - rejections.values.sum(),
        rejections = rejections,
        missingBackdrop = candidates.count { !spotlightHasBackdrop(it.movie) },
        omittedSourceCount = candidates.count { it.movie.mediaSourceCount == null },
    )
}

private data class SpotlightRank(val tier: Int, val band: Int)
private fun spotlightRank(movie: BaseItemDto): SpotlightRank {
    val rating = spotlightRatings(movie)
    return when {
        rating.critic != null && rating.critic >= 70f ->
            SpotlightRank(0, (rating.critic.toInt() / 10).coerceAtMost(9))
        rating.community != null && rating.community >= 7f ->
            SpotlightRank(1, rating.community.toInt().coerceAtMost(9))
        else -> SpotlightRank(2, 0)
    }
}

/** Separate scales, coarse bands, no fabricated votes or provider attribution. */
internal fun selectSpotlightMovies(
    candidates: List<SpotlightCandidate>,
    scope: SpotlightScope,
    epochDay: Long,
    limit: Int = 5,
    collections: Map<UUID, Set<UUID>> = emptyMap(),
): List<BaseItemDto> {
    if (limit <= 0) return emptyList()
    val sorted = candidates.asSequence().filter { spotlightEligible(it, scope) }
        .map { it.movie }.distinctBy { it.id }
        .sortedWith(compareBy<BaseItemDto> { spotlightRank(it).tier }
            .thenByDescending { spotlightRank(it).band }
            .thenByDescending { spotlightHasBackdrop(it) }
            .thenByDescending { it.dateCreated?.toString().orEmpty() }
            .thenBy { it.sortName ?: it.name.orEmpty() }
            .thenBy { it.id.toString() })
        .toList()
    val count = limit.coerceAtMost(5)
    val rated = sorted.filter { spotlightRank(it).tier < 2 }
    // Broaden variety within the existing bounded pool, still requiring the same quality bands.
    // Only use recency fallback when there are too few well-rated titles.
    val shortlist = if (rated.size >= count) rated
        else rated + sorted.filter { spotlightRank(it).tier == 2 }.take(20)
    if (shortlist.isEmpty()) return emptyList()
    val size = shortlist.size.toLong()
    // A coprime stride visits every lead before reuse; fixed stride5 repeats early for N=5/20.
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    var stride = 5
    while (gcd(stride, shortlist.size) != 1) stride++
    val accountOffset = listOf(scope.serverId, scope.userId, scope.libraryId)
        .joinToString(":").hashCode().toLong()
    val start = Math.floorMod(Math.floorMod(epochDay, size) * stride + accountOffset, size).toInt()
    val rotated = List(shortlist.size) { shortlist[(start + it) % shortlist.size] }
    val selected = mutableListOf<BaseItemDto>()
    val represented = mutableSetOf<UUID>()
    // Verified BoxSet membership only. An unknown membership never implies a franchise.
    // Keep the existing daily lead traversal; skip siblings before filling remaining places.
    if (collections.isEmpty()) return rotated.take(count)
    // Adjacent skipping alone repeatedly favors the first title in each collection.
    // Mix only tie-breaks inside the existing quality/artwork bands, once per daily load.
    fun varietyKey(movie: BaseItemDto): Long {
        var value = movie.id.mostSignificantBits xor
            java.lang.Long.rotateLeft(movie.id.leastSignificantBits, 23) xor
            (epochDay * -7046029254386353131L) xor accountOffset
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
    val lead = rotated.first()
    val diversityPool = listOf(lead) + sorted.filter { it.id != lead.id }.sortedWith(
        compareBy<BaseItemDto> { spotlightRank(it).tier }
            .thenByDescending { spotlightRank(it).band }
            .thenByDescending { spotlightHasBackdrop(it) }
            .thenBy { varietyKey(it) }.thenBy { it.id.toString() },
    )
    for (movie in diversityPool) {
        val memberships = collections[movie.id].orEmpty()
        if (memberships.none { it in represented }) {
            selected += movie
            represented += memberships
            if (selected.size == count) return selected
        }
    }
    // Small pools or ubiquitous collections cannot provide impossible variety.
    return (selected + rotated).distinctBy { it.id }.take(count)
}

internal fun spotlightResumeMs(movie: BaseItemDto): Long {
    val runtime = movie.runTimeTicks ?: return 0
    val resume = movie.userData?.playbackPositionTicks ?: return 0
    return if (movie.userData?.played != true && runtime > 0 && resume > 0 && resume < runtime)
        resume / 10_000 else 0
}
internal fun spotlightPlayback(movie: BaseItemDto) =
    Destination.Playback(itemId = movie.id, positionMs = spotlightResumeMs(movie))
internal fun spotlightDetails(movie: BaseItemDto) =
    Destination.MediaItem(movie.id, BaseItemKind.MOVIE)

internal data class HomeWatchProgress(val fraction: Float?, val remainingTicks: Long?) {
    val remainingMinutes: Long? get() = remainingTicks?.let {
        it / 600_000_000L + if (it % 600_000_000L == 0L) 0 else 1
    }
}
internal fun homeWatchProgress(movie: BaseItemDto): HomeWatchProgress {
    if (movie.userData?.played == true) return HomeWatchProgress(null, null)
    val runtime = movie.runTimeTicks
    val resume = movie.userData?.playbackPositionTicks
    val fraction = if (runtime != null && runtime > 0 && resume != null && resume >= 0)
        (resume.toDouble() / runtime.toDouble()).coerceIn(0.0, 1.0).toFloat()
    else movie.userData?.playedPercentage?.takeIf { it.isFinite() }?.div(100)?.coerceIn(0.0, 1.0)?.toFloat()
    val remaining = if (movie.type in listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE) &&
        runtime != null && runtime > 0 && resume != null && resume > 0 && resume < runtime)
        runtime - resume else null
    return HomeWatchProgress(fraction, remaining)
}
internal fun homeResumeRow(row: HomeRowConfig?) =
    row is HomeRowConfig.ContinueWatching || row is HomeRowConfig.ContinueWatchingCombined || row is HomeRowConfig.NextUp

/** Presentation only: never mutates, drops or reorders configured rows. */
internal fun compactHomeOptions(config: HomeRowConfig?, options: HomeRowViewOptions): HomeRowViewOptions {
    if (options != HomeRowViewOptions()) return options
    return when {
        homeResumeRow(config) -> options.copy(
            heightDp = 90, spacing = 12, aspectRatio = AspectRatio.WIDE,
            episodeAspectRatio = AspectRatio.WIDE, imageType = ViewOptionImageType.THUMB,
            episodeImageType = ViewOptionImageType.THUMB, showTitles = true,
        )
        config is HomeRowConfig.RecentlyAdded -> options.copy(heightDp = 144, spacing = 12)
        else -> options
    }
}

internal enum class HomeEntryTarget { SPOTLIGHT, ROW }
internal fun homeEntryTarget(rememberedRow: Int, spotlightAvailable: Boolean) =
    if (rememberedRow < 0 && spotlightAvailable) HomeEntryTarget.SPOTLIGHT else HomeEntryTarget.ROW
