package com.github.damontecres.wholphin.ui.main

import coil3.memory.MemoryCache

/** Retains at most eight successful cache keys, never images. A cache miss/eviction
 * still shows the text fallback. Coil owns all decoded bitmap retention.
 */
internal class SpotlightLogoReadiness(private val capacity: Int = 8) {
    init { require(capacity > 0) }
    private val ready = linkedMapOf<String, MemoryCache.Key>()
    fun contains(url: String?, cache: MemoryCache?): Boolean =
        ready[url]?.let { cache?.get(it) != null } == true

    fun success(url: String, key: MemoryCache.Key?) {
        ready.remove(url)
        if (key != null) ready[url] = key
        if (ready.size > capacity) ready.remove(ready.keys.first())
    }
    fun failure(url: String) { ready.remove(url) }
}
