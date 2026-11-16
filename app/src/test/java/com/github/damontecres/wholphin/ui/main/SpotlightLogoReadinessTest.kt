package com.github.damontecres.wholphin.ui.main
import coil3.Image
import coil3.memory.MemoryCache
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class SpotlightLogoReadinessTest {
    private val entries = mutableMapOf<MemoryCache.Key, MemoryCache.Value>()
    private val cache = mockk<MemoryCache>().also { memory ->
        every { memory[any<MemoryCache.Key>()] } answers { entries[firstArg<MemoryCache.Key>()] }
    }
    private fun success(readiness: SpotlightLogoReadiness, url: String) {
        val key = MemoryCache.Key(url, mapOf("actual-request-extra" to "preserved"))
        entries[key] = MemoryCache.Value(mockk<Image>())
        readiness.success(url, key)
    }
    @Test fun warmPagingDoesNotReenterFallbackForDecodedSuccessfulLogos() {
        val readiness = SpotlightLogoReadiness()
        var fallbackEntries = 0
        repeat(24) { i ->
            val logo = "fixture-logo-" + i % 5
            if (!readiness.contains(logo, cache)) fallbackEntries++
            success(readiness, logo)
        }
        assertEquals(5, fallbackEntries) // Previous per-change state reset began false24 times.
        assertFalse(readiness.contains(null, cache))
        assertFalse(readiness.contains("unseen", cache))
    }
    @Test fun evictionAndDisabledCachingAlwaysRestoreFallback() {
        val readiness = SpotlightLogoReadiness()
        success(readiness, "a")
        assertTrue(readiness.contains("a", cache))
        entries.clear()
        assertFalse(readiness.contains("a", cache))
        assertFalse(readiness.contains("a", null))
        readiness.success("a", null)
        assertFalse(readiness.contains("a", cache))
    }
    @Test fun failedCachedLogoAlwaysRestoresFallback() {
        val readiness = SpotlightLogoReadiness()
        success(readiness, "a")
        readiness.failure("a")
        assertFalse(readiness.contains("a", cache))
        success(readiness, "a")
        assertTrue(readiness.contains("a", cache))
    }
    @Test fun keyHistoryIsBoundedWithoutRetainingBitmaps() {
        val readiness = SpotlightLogoReadiness(2)
        success(readiness, "a"); success(readiness, "b"); success(readiness, "a"); success(readiness, "c")
        assertTrue(readiness.contains("a", cache))
        assertTrue(readiness.contains("c", cache))
        assertFalse(readiness.contains("b", cache))
        assertEquals(3, entries.size) // Does not clear or resize Coil's global cache.
    }
}
