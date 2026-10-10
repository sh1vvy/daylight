package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import com.music.bitchord.data.model.Song

/** Streaming and device tracks carry their own artwork URI. */
@Composable
fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl
