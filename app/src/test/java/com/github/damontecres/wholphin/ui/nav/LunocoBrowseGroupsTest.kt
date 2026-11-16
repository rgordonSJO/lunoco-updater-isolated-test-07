package com.github.damontecres.wholphin.ui.nav

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LunocoBrowseGroupsTest {
    private fun library(name: String, type: CollectionType, recordings: Boolean = false): ServerNavDrawerItem {
        val id = UUID.randomUUID()
        return ServerNavDrawerItem(id, name,
            if (recordings) Destination.Recordings(id)
            else Destination.MediaItem(id, BaseItemKind.COLLECTION_FOLDER, type), type)
    }
    private val permitted = listOf(
        library("Movies", CollectionType.MOVIES), library("Kids Movies", CollectionType.MOVIES),
        library("Shows", CollectionType.TVSHOWS), library("Kids Shows", CollectionType.TVSHOWS),
        library("Live TV", CollectionType.LIVETV), library("DVR", CollectionType.UNKNOWN, true),
        library("Home Movies", CollectionType.HOMEVIDEOS),
    )

    @Test fun actualPopupPositionStaysInside1080pAndAnchorsBelowOrAbove() {
        val provider = BrowseMenuPosition(8, 16)
        val screen = androidx.compose.ui.unit.IntSize(1920, 1080)
        val popup = androidx.compose.ui.unit.IntSize(448, 224)
        val direction = androidx.compose.ui.unit.LayoutDirection.Ltr
        val normal = provider.calculatePosition(androidx.compose.ui.unit.IntRect(420, 16, 600, 84), screen, direction, popup)
        assertEquals(androidx.compose.ui.unit.IntOffset(420, 92), normal)
        val edge = provider.calculatePosition(androidx.compose.ui.unit.IntRect(1800, 1000, 1900, 1068), screen, direction, popup)
        assertTrue(edge.x >= 16 && edge.x + popup.width <= screen.width - 16)
        assertTrue(edge.y >= 16 && edge.y + popup.height <= 1000 - 8)
        val rtl = provider.calculatePosition(androidx.compose.ui.unit.IntRect(10, 16, 200, 84),
            screen, androidx.compose.ui.unit.LayoutDirection.Rtl, popup)
        assertEquals(16, rtl.x)
    }

    @Test fun fiveDestinationPresentationKeepsAllSevenResolvedLibraryLinks() {
        val links = jellyLuBrowseLinks(permitted)
        val groups = lunocoBrowseGroups(links)
        assertEquals(listOf(JellyLuBrowseTab.MOVIES, JellyLuBrowseTab.SHOWS,
            JellyLuBrowseTab.LIVE_TV, JellyLuBrowseTab.HOME_MOVIES), groups.map { it.tab })
        assertEquals(links.toSet(), groups.flatMap { it.children }.toSet())
        assertFalse(groups.last().hasMenu)
        assertTrue(groups.dropLast(1).all { it.hasMenu })
    }

    @Test fun moviesAndShowsKeepPrimaryThenKidsAndDirectGridIds() {
        val groups = lunocoBrowseGroups(jellyLuBrowseLinks(permitted))
        assertEquals(listOf(JellyLuBrowseTab.MOVIES, JellyLuBrowseTab.KIDS_MOVIES), groups[0].children.map { it.tab })
        assertEquals(listOf(JellyLuBrowseTab.SHOWS, JellyLuBrowseTab.KIDS_SHOWS), groups[1].children.map { it.tab })
        groups.take(2).flatMap { it.children }.forEach {
            val target = it.destination as Destination.MediaItem
            assertTrue(target.browseGrid)
            assertEquals(it.library.itemId, target.itemId)
        }
    }

    @Test fun guideAndDvrRetainTheirOriginalDestinations() {
        val group = lunocoBrowseGroups(jellyLuBrowseLinks(permitted))[2]
        assertEquals(listOf(JellyLuBrowseTab.LIVE_TV, JellyLuBrowseTab.DVR), group.children.map { it.tab })
        group.children.forEach { assertEquals(it.library.destination, it.destination) }
    }

    @Test fun inaccessiblePrimaryDoesNotGetInventedOrRedirectToKids() {
        val kids = permitted[1]
        val groups = lunocoBrowseGroups(jellyLuBrowseLinks(listOf(kids)))
        assertEquals(1, groups.size)
        assertEquals(listOf(JellyLuBrowseTab.KIDS_MOVIES), groups.single().children.map { it.tab })
        assertEquals(kids.itemId, groups.single().children.single().library.itemId)
        assertTrue(lunocoBrowseGroups(emptyList()).isEmpty())
    }

    @Test fun dvrCanRemainAvailableWithoutGuideAndPersonalVideoIsDirect() {
        val groups = lunocoBrowseGroups(jellyLuBrowseLinks(permitted.takeLast(2)))
        assertEquals(listOf(JellyLuBrowseTab.LIVE_TV, JellyLuBrowseTab.HOME_MOVIES), groups.map { it.tab })
        assertEquals(JellyLuBrowseTab.DVR, groups[0].children.single().tab)
        assertFalse(groups[1].hasMenu)
    }
}
