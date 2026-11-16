package com.github.damontecres.wholphin.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class JellyLuHomePresentationTest {
    @Test
    fun visibleCardsStayStillAndOffscreenCardsScrollOnlyAsNeeded() {
        val spec = JellyLuHomeBringIntoViewSpec(20f)
        assertEquals(0f, spec.calculateScrollDistance(270f, 124f, 528f), 0.01f)
        assertEquals(-12f, spec.calculateScrollDistance(8f, 124f, 528f), 0.01f)
        assertEquals(66f, spec.calculateScrollDistance(450f, 124f, 528f), 0.01f)
        assertEquals(80f, spec.calculateScrollDistance(100f, 700f, 528f), 0.01f)
        // Home's 50dp title allowance must reveal the heading above an upward-focused card.
        val withHeading = JellyLuHomeBringIntoViewSpec(50f)
        assertEquals(-30f, withHeading.calculateScrollDistance(20f, 172f, 528f), 0.01f)
        assertEquals(0f, withHeading.calculateScrollDistance(280f, 124f, 528f), 0.01f)
    }
}
