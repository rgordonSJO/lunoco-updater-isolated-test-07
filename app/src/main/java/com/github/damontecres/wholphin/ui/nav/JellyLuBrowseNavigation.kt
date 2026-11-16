package com.github.damontecres.wholphin.ui.nav

import com.github.damontecres.wholphin.R
import org.jellyfin.sdk.model.api.CollectionType
import java.util.Locale

/** The product order is independent of server IDs, pin order and Home row preferences. */
enum class JellyLuBrowseTab(val label: Int, val widthDp: Int) {
    HOME(R.string.home, 48),
    MOVIES(R.string.jellylu_nav_movies, 60),
    SHOWS(R.string.jellylu_shows, 56),
    KIDS_MOVIES(R.string.jellylu_kids_movies, 96),
    KIDS_SHOWS(R.string.jellylu_kids_shows, 92),
    LIVE_TV(R.string.jellylu_live_tv, 68),
    DVR(R.string.jellylu_dvr, 44),
    HOME_MOVIES(R.string.jellylu_home_movies, 106),
}

data class JellyLuBrowseLink(
    val tab: JellyLuBrowseTab,
    val library: ServerNavDrawerItem,
) {
    val destination: Destination
        get() = when (val target = library.destination) {
            is Destination.MediaItem -> if (library.type in listOf(
                    CollectionType.MOVIES, CollectionType.TVSHOWS, CollectionType.HOMEVIDEOS,
                )) target.copy(browseGrid = true) else target
            else -> target
        }
}

private fun String.libraryNameKey(): String =
    lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

/**
 * Resolve only libraries supplied for this user. Prefer the requested semantic name;
 * an ambiguous category stays in profile overflow rather than choosing an arbitrary library.
 * No requests, persistent preference edits or server-specific IDs are involved.
 */
fun jellyLuBrowseLinks(entries: List<NavDrawerItem>): List<JellyLuBrowseLink> {
    val libraries = entries.filterIsInstance<ServerNavDrawerItem>().distinctBy { it.itemId }
    fun named(type: CollectionType, names: Set<String>, fallback: Boolean = false): ServerNavDrawerItem? {
        val candidates = libraries.filter { it.type == type && it.destination !is Destination.Recordings }
        val exact = candidates.filter { it.name.libraryNameKey() in names }
        if (exact.size == 1) return exact.single()
        if (exact.isNotEmpty() || !fallback) return null
        // A unique personal-video library may legitimately be named Family Memories.
        // Excluding family/kids is only needed when separating general movie/TV categories.
        return candidates.filterNot {
            val name = it.name.libraryNameKey()
            type != CollectionType.HOMEVIDEOS &&
                (name.contains("kids") || name.contains("children") || name.contains("family"))
        }.singleOrNull()
    }
    return buildList {
        named(CollectionType.MOVIES, setOf("movies", "films"), true)?.let {
            add(JellyLuBrowseLink(JellyLuBrowseTab.MOVIES, it))
        }
        named(CollectionType.TVSHOWS, setOf("shows", "tvshows", "television"), true)?.let {
            add(JellyLuBrowseLink(JellyLuBrowseTab.SHOWS, it))
        }
        named(CollectionType.MOVIES, setOf("kidsmovies", "childrensmovies", "childrenmovies"))?.let {
            add(JellyLuBrowseLink(JellyLuBrowseTab.KIDS_MOVIES, it))
        }
        named(CollectionType.TVSHOWS, setOf("kidsshows", "kidstvshows", "childrensshows", "childrenshows"))?.let {
            add(JellyLuBrowseLink(JellyLuBrowseTab.KIDS_SHOWS, it))
        }
        libraries.filter { it.type == CollectionType.LIVETV && it.destination !is Destination.Recordings }
            .singleOrNull()?.let { add(JellyLuBrowseLink(JellyLuBrowseTab.LIVE_TV, it)) }
        val recordings = libraries.filter { it.destination is Destination.Recordings }
        val namedDvr = recordings.filter { it.name.libraryNameKey() in setOf("dvr", "recordings") }
        (namedDvr.singleOrNull() ?: recordings.singleOrNull())?.let {
            add(JellyLuBrowseLink(JellyLuBrowseTab.DVR, it))
        }
        named(CollectionType.HOMEVIDEOS, setOf("homemovies", "homevideos", "personalvideos"), true)?.let {
            add(JellyLuBrowseLink(JellyLuBrowseTab.HOME_MOVIES, it))
        }
    }
}
