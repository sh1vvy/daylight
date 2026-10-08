package com.music.bitchord.ui.screens

import androidx.compose.runtime.saveable.listSaver

/** Only manual choices are saved; a search temporarily reveals matching controls. */
internal data class SettingsCategoryExpansion(
    private val expandedIds: Set<String> = emptySet(),
) {
    fun isExpanded(categoryId: String, query: String): Boolean =
        query.isNotBlank() || categoryId in expandedIds

    fun toggle(categoryId: String): SettingsCategoryExpansion = copy(
        expandedIds = if (categoryId in expandedIds) expandedIds - categoryId else expandedIds + categoryId,
    )

    companion object {
        val Saver = listSaver<SettingsCategoryExpansion, String>(
            save = { it.expandedIds.toList() },
            restore = { SettingsCategoryExpansion(it.toSet()) },
        )
    }
}

/** Each composition collects matches before deciding whether to show the empty state. */
internal class SettingsSearch(query: String) {
    private val needle = query.trim()
    val active: Boolean get() = needle.isNotEmpty()

    var anyMatch = false
        private set

    fun matches(vararg keywords: String?): Boolean {
        val hit = !active || keywords.any { it?.contains(needle, ignoreCase = true) == true }
        if (hit) anyMatch = true
        return hit
    }
}
