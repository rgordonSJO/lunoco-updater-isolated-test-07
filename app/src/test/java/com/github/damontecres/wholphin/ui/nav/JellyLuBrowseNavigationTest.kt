package com.github.damontecres.wholphin.ui.nav

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class JellyLuBrowseNavigationTest {
    private fun library(name: String, type: CollectionType, recording: Boolean = false): ServerNavDrawerItem {
        val id = UUID.randomUUID()
        return ServerNavDrawerItem(
            id, name,
            if (recording) Destination.Recordings(id)
            else Destination.MediaItem(id, BaseItemKind.COLLECTION_FOLDER, type),
            type,
        )
    }

    @Test fun exactOrderUsesRealLibraryIdsRegardlessOfPinOrder() {
        val libraries = listOf(
            library("Home Movies", CollectionType.HOMEVIDEOS),
            library("DVR", CollectionType.UNKNOWN, true),
            library("Kids Shows", CollectionType.TVSHOWS),
            library("Movies", CollectionType.MOVIES),
            library("Live TV", CollectionType.LIVETV),
            library("Kids Movies", CollectionType.MOVIES),
            library("Shows", CollectionType.TVSHOWS),
        )
        val links = jellyLuBrowseLinks(libraries.reversed())
        assertEquals(JellyLuBrowseTab.entries.drop(1), links.map { it.tab })
        assertEquals(libraries.map { it.itemId }.toSet(), links.map { it.library.itemId }.toSet())
    }

    @Test fun absentOrInaccessibleLibrariesAreNeverInvented() {
        assertTrue(jellyLuBrowseLinks(listOf(NavDrawerItem.Favorites)).isEmpty())
        val kids = library("Kids Movies", CollectionType.MOVIES)
        val links = jellyLuBrowseLinks(listOf(kids))
        assertEquals(listOf(JellyLuBrowseTab.KIDS_MOVIES), links.map { it.tab })
        assertEquals(kids.itemId, links.single().library.itemId)
    }

    @Test fun ambiguousLibrariesRemainAvailableForOverflowWithoutArbitrarySelection() {
        val entries = listOf(library("Cinema", CollectionType.MOVIES), library("Documentaries", CollectionType.MOVIES))
        assertTrue(jellyLuBrowseLinks(entries).isEmpty())
        assertEquals(2, entries.size) // Resolver never mutates the input/permissions list.
    }

    @Test fun namedPrimaryDoesNotAbsorbKidsOrAdditionalLibraries() {
        val primary = library("Movies", CollectionType.MOVIES)
        val kids = library("Kids Movies", CollectionType.MOVIES)
        val extra = library("Documentaries", CollectionType.MOVIES)
        val links = jellyLuBrowseLinks(listOf(extra, kids, primary, primary))
        assertEquals(listOf(primary.itemId, kids.itemId), links.map { it.library.itemId })
        assertFalse(extra.itemId in links.map { it.library.itemId })
    }

    @Test fun gridsBypassOverviewWhileLiveTvAndRecordingsKeepExistingDestinations() {
        val entries = listOf(
            library("Movies", CollectionType.MOVIES),
            library("Shows", CollectionType.TVSHOWS),
            library("Kids Movies", CollectionType.MOVIES),
            library("Kids Shows", CollectionType.TVSHOWS),
            library("Home Movies", CollectionType.HOMEVIDEOS),
            library("Live TV", CollectionType.LIVETV),
            library("DVR", CollectionType.UNKNOWN, true),
        )
        jellyLuBrowseLinks(entries).forEach { link ->
            val target = link.destination
            if (target is Destination.MediaItem && link.tab != JellyLuBrowseTab.LIVE_TV) {
                assertTrue(target.browseGrid)
                assertEquals(link.library.itemId, target.itemId)
            } else assertEquals(link.library.destination, target)
        }
        // Existing My Libraries/overview links are not globally redirected.
        assertFalse((entries.first().destination as Destination.MediaItem).browseGrid)
    }

    @Test fun uniquePersonalVideoLibraryCanBeNamedFamilyMemories() {
        val family = library("Family Memories", CollectionType.HOMEVIDEOS)
        val link = jellyLuBrowseLinks(listOf(family)).single()
        assertEquals(JellyLuBrowseTab.HOME_MOVIES, link.tab)
        assertEquals(family.itemId, (link.destination as Destination.MediaItem).itemId)
        assertTrue((link.destination as Destination.MediaItem).browseGrid)
    }

    @Test fun semanticNamesIgnoreCaseSpacingAndPunctuation() {
        val kids = library(" kids-movies ", CollectionType.MOVIES)
        assertEquals(JellyLuBrowseTab.KIDS_MOVIES, jellyLuBrowseLinks(listOf(kids)).single().tab)
    }

    @Test fun uniqueNonKidsCategoryCanUseServerSpecificNameWithoutHardcodedId() {
        val library = library("Cinema", CollectionType.MOVIES)
        assertEquals(library.itemId, jellyLuBrowseLinks(listOf(library)).single().library.itemId)
    }
}
