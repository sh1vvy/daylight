/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

/** Original Daylight queue projection. Indices always refer to the audio owner's timeline. */
internal data class UpcomingTrack(val key: String, val index: Int, val track: PlayerTrack)

internal fun upcomingTracks(queue: List<PlayerTrack>, playingIndex: Int): List<UpcomingTrack> {
    val occurrences = HashMap<String, Int>()
    val keys = queue.map { track ->
        val occurrence = occurrences.getOrDefault(track.key, 0)
        occurrences[track.key] = occurrence + 1
        // Length-prefixing avoids collisions between an entry ID and its occurrence suffix.
        "${track.key.length}:${track.key}:$occurrence"
    }
    val start = (playingIndex.toLong() + 1).coerceIn(0, queue.size.toLong()).toInt()
    return (start until queue.size).map { UpcomingTrack(keys[it], it, queue[it]) }
}

/** Reordering cannot cross manual/collection/radio boundaries or move the current track. */
internal fun previewQueueMove(rows: List<UpcomingTrack>, key: String, target: String): List<UpcomingTrack> {
    val from = rows.indexOfFirst { it.key == key }
    val to = rows.indexOfFirst { it.key == target }
    if (from < 0 || to < 0 || from == to) return rows
    val lane = rows[from].track.lane
    if ((minOf(from, to)..maxOf(from, to)).any { rows[it].track.lane != lane }) return rows
    return rows.toMutableList().apply { add(to, removeAt(from)) }
}

/** A drop changes order once; the audio owner receives original absolute indices. */
internal fun queueDropMove(original: List<UpcomingTrack>, preview: List<UpcomingTrack>, key: String): Pair<Int, Int>? {
    if (original.map { it.key }.toSet() != preview.map { it.key }.toSet()) return null
    val from = original.indexOfFirst { it.key == key }
    val to = preview.indexOfFirst { it.key == key }
    if (from < 0 || to < 0 || from == to) return null
    return original[from].index to original[to].index
}
