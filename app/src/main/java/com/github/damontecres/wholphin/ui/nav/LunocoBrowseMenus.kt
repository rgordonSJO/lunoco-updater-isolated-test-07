package com.github.damontecres.wholphin.ui.nav

/** Presentation groups over the existing permission-scoped resolver; never invents routes. */
internal data class LunocoBrowseGroup(
    val tab: JellyLuBrowseTab,
    val children: List<JellyLuBrowseLink>,
) {
    val hasMenu get() = tab != JellyLuBrowseTab.HOME_MOVIES
}

internal fun lunocoBrowseGroups(links: List<JellyLuBrowseLink>): List<LunocoBrowseGroup> =
    listOf(
        JellyLuBrowseTab.MOVIES to setOf(JellyLuBrowseTab.MOVIES, JellyLuBrowseTab.KIDS_MOVIES),
        JellyLuBrowseTab.SHOWS to setOf(JellyLuBrowseTab.SHOWS, JellyLuBrowseTab.KIDS_SHOWS),
        JellyLuBrowseTab.LIVE_TV to setOf(JellyLuBrowseTab.LIVE_TV, JellyLuBrowseTab.DVR),
        JellyLuBrowseTab.HOME_MOVIES to setOf(JellyLuBrowseTab.HOME_MOVIES),
    ).mapNotNull { (tab, members) ->
        links.filter { it.tab in members }.sortedBy { it.tab != tab }
            .takeIf { it.isNotEmpty() }?.let { LunocoBrowseGroup(tab, it) }
    }
