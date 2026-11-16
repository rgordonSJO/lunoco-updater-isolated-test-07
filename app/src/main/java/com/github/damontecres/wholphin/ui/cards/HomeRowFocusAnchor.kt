package com.github.damontecres.wholphin.ui.cards

import androidx.compose.runtime.saveable.listSaver

/** Saveable identity without observable writes/recomposition on every D-pad focus movement. */
internal class HomeRowFocusAnchor(var index: Int = 0, var key: String? = null) {
    fun record(index: Int, key: String?) {
        this.index = index
        this.key = key
    }

    fun resolve(keys: List<String?>): Int {
        val existing = key?.let { keys.indexOf(it).takeIf { index -> index >= 0 } }
        return existing ?: keys.indices.filter { keys[it] != null }
            .minByOrNull { kotlin.math.abs(it.toLong() - index.toLong()) } ?: 0
    }

    companion object {
        val Saver = listSaver<HomeRowFocusAnchor, Any>(
            save = { listOf(it.index, it.key.orEmpty()) },
            restore = { HomeRowFocusAnchor(it[0] as Int, (it[1] as String).takeIf(String::isNotEmpty)) },
        )
    }
}
