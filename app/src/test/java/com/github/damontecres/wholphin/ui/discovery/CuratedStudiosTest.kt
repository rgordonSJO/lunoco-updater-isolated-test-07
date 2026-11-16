package com.github.damontecres.wholphin.ui.discovery

import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CuratedStudiosTest {
    private fun studio(n: Int, name: String, count: Int? = 1) =
        BaseItemDto(id = UUID(0, n.toLong()), name = name, type = BaseItemKind.STUDIO, movieCount = count)
    private val scope = MovieExploreScope(UUID(1,1), UUID(2,2), UUID(3,3))

    @Test fun recognizableStudiosBeyondAnAlphabeticalPageAreSelectedInCuratedOrder() {
        val obscure = (1..80).map { studio(it, "A obscure production $it") }
        val items = listOf(studio(100,"Pixar"), studio(101,"Walt Disney Pictures"), studio(102,"Warner Bros."))
        assertEquals(listOf("Walt Disney Pictures","Pixar","Warner Bros."),
            curatedStudioFacets(obscure + items).map { it.name })
        assertEquals(curatedStudioFacets(obscure + items), curatedStudioFacets((obscure + items).reversed()))
    }

    @Test fun onlyExactVerifiedAliasesMergeAndAllIdsReachTheExistingFilter() {
        val items = listOf(studio(1,"Sony Pictures"), studio(2,"Columbia Pictures"),
            studio(3,"Sony Music"), studio(4,"Columbia Broadcasting System"),
            studio(5,"Walt Disney Television"), studio(6,"1492 Pictures"))
        val facet = curatedStudioFacets(items).single()
        assertEquals(listOf(items[0].id,items[1].id), facet.studioIds)
        val destination = facet.destination(scope)
        assertEquals(scope.library, destination.itemId)
        assertEquals(facet.studioIds, destination.filter.filter.studios)
        assertEquals(facet.studioIds, facetMovieRequest(scope, facet).studioIds)
    }

    @Test fun historicalTwentiethAliasesMatchButUnrelatedAnimationCompanyIsNotGuessed() {
        val result = curatedStudioFacets(listOf(studio(1,"20th Century Fox"),
            studio(2,"20th Century Studios"), studio(3,"20th Century Fox Animation")))
        assertEquals(2, result.single().studioIds.size)
        assertEquals("STUDIO:twentieth", result.single().key)
    }

    @Test fun nullArtworkKeepsValidCardAndLogoMustComeFromAStudio() {
        val input = studio(1,"Pixar")
        assertNull(curatedStudioFacets(listOf(input)).single().image)
        val logo = input.copy(imageTags = mapOf(ImageType.LOGO to "authentic"))
        assertEquals(input.id, curatedStudioFacets(listOf(logo)).single().image!!.item)
        assertTrue(curatedStudioFacets(listOf(logo.copy(type = BaseItemKind.MOVIE))).isEmpty())
    }

    @Test fun mergedAliasesPreferAnAuthenticLogoOverAnotherAliasPrimaryImage() {
        val old = studio(1,"20th Century Fox").copy(imageTags = mapOf(ImageType.PRIMARY to "primary"))
        val current = studio(2,"20th Century Studios").copy(imageTags = mapOf(ImageType.LOGO to "logo"))
        val facet = curatedStudioFacets(listOf(old,current)).single()
        assertEquals(ImageType.LOGO, facet.image!!.type)
        assertEquals(current.id, facet.image!!.item)
        assertEquals(listOf(old.id,current.id), facet.studioIds)
    }

    @Test fun blankZeroOrEmptyCategoriesCannotBecomeCuratedStudios() {
        assertTrue(curatedStudioFacets(listOf(studio(1,"Pixar",0), studio(2,""), studio(0,"A24"))).isEmpty())
        assertTrue(curatedStudioFacets(emptyList()).isEmpty())
    }

    @Test fun targetedQueriesScopeEveryPageAndNeverUseGlobalFirstFortyEight() {
        val request = curatedStudioRequest(scope, "Disney", 1)
        assertEquals("Disney", request.searchTerm)
        assertEquals(24, request.startIndex)
        assertEquals(24, request.limit)
        assertEquals(scope.user, request.userId)
        assertEquals(scope.library, request.parentId)
        assertEquals(listOf(BaseItemKind.MOVIE), request.includeItemTypes)
    }

    @Test fun homePresentationRowsDoNotRewriteConfiguredRowsAndCarryRealScopedDestinations() {
        val state = MovieExploreState(scope = scope, studios = curatedStudioFacets(listOf(studio(1,"Pixar"))))
        val rows = homeDiscoveryRows(state)
        assertEquals(listOf("lunoco_discovery:studios","lunoco_discovery:genres"), rows.map { it.presentationKey })
        assertFalse(rows[0].showViewMore)
        assertNull(rows[0].rowType)
        assertEquals(state.studios.single().destination(scope), rows[0].items.single()!!.destinationOverride)
        assertTrue(rows[1].items.isEmpty())
        assertEquals(rows.map { it.presentationKey }, homeDiscoveryRows(MovieExploreState()).map { it.presentationKey })
    }
}
