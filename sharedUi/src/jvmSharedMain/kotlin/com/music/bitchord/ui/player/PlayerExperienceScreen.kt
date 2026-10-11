/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.player

import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.lyrics.*
import com.music.bitchord.data.model.*
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.theme.*
import com.music.bitchord.sharedui.resources.*
import com.sh1vvy.daylight.player.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/** Transitional adapter only: existing audio/providers/Automix supply the independently drawn player. */
@Composable
fun PlayerExperienceScreen(
    experience: PlayerExperience,
    onDismiss: () -> Unit,

    song: Song,

    playedBy: String? = null,
    isPlaying: Boolean,
    isLoading: Boolean,

    position: PlaybackPosition,
    durationMs: Long,

    audioVersionSwitching: Boolean,

    qualityUpgraded: Boolean,
    queue: List<Song>,
    queueIndex: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    autoplayEnabled: Boolean,
    signedIn: Boolean,
    accountName: String?,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,

    onBlockedControl: () -> Unit,
    onSeek: (Long) -> Unit,

    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,

    onQueueDragActiveChange: (Boolean) -> Unit = {},
    onClearQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    onMenuAnchor: (Rect) -> Unit = {},
    onOpenAlbum: (String) -> Unit,

    onOpenArtist: (browseId: String?, name: String) -> Unit,

    onOpenPlaybackSource: () -> Unit,

    onListenTogether: () -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsProviderStates: Map<LyricsSource, LyricsProviderState>,
    onSelectLyricsProvider: (LyricsSource) -> Unit,
    lyricsUnavailable: Boolean,
    lyricsOffsetOpen: Boolean,
    onDismissLyricsOffset: () -> Unit,

    windowWidth: Dp,

    windowHeight: Dp,
    modifier: Modifier = Modifier,

    lyricsMissing: Boolean = false,
) {
    if (experience == PlayerExperience.CURRENT) {
        NowPlayingScreen(
            song = song,
            playedBy = playedBy,
            isPlaying = isPlaying,
            isLoading = isLoading,
            position = position,
            durationMs = durationMs,
            audioVersionSwitching = audioVersionSwitching,
            qualityUpgraded = qualityUpgraded,
            queue = queue,
            queueIndex = queueIndex,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            autoplayEnabled = autoplayEnabled,
            signedIn = signedIn,
            accountName = accountName,
            likeStatus = likeStatus,
            onToggleLike = onToggleLike,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onBlockedControl = onBlockedControl,
            onSeek = onSeek,
            onSeekFraction = onSeekFraction,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onToggleAutoplay = onToggleAutoplay,
            onJumpTo = onJumpTo,
            onRemoveFromQueue = onRemoveFromQueue,
            onMoveInQueue = onMoveInQueue,
            onQueueDragActiveChange = onQueueDragActiveChange,
            onClearQueue = onClearQueue,
            onOpenMenu = onOpenMenu,
            onMenuAnchor = onMenuAnchor,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenPlaybackSource = onOpenPlaybackSource,
            onListenTogether = onListenTogether,
            lyrics = lyrics,
            lyricsSource = lyricsSource,
            lyricsProviderStates = lyricsProviderStates,
            onSelectLyricsProvider = onSelectLyricsProvider,
            lyricsUnavailable = lyricsUnavailable,
            lyricsOffsetOpen = lyricsOffsetOpen,
            onDismissLyricsOffset = onDismissLyricsOffset,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            modifier = modifier,
            lyricsMissing = lyricsMissing,
        )
        return
    }
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val offset by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val controlsLocked = rememberControlsLocked()
    var panel by rememberSaveablePlayerPanel()
    PlayerBackHandler(panel != PlayerPanel.ARTWORK) { panel = PlayerPanel.ARTWORK }
    val haze = remember { HazeState() }
    var outputOpen by remember { mutableStateOf(false) }
    var pipelineOpen by remember { mutableStateOf(false) }
    var castOpen by remember { mutableStateOf(false) }
    var providersOpen by remember { mutableStateOf(false) }
    var offsetOpen by remember { mutableStateOf(false) }
    var artworkOpen by remember { mutableStateOf(false) }
    val openOutput = rememberOutputPicker { outputOpen = true }
    val remoteArt = rememberRemoteArtworkUrl(song)
    val art = rememberPlayerArtwork(remoteArt)
    val palette = rememberArtworkPalette(remoteArt, artPx = ART_PX)
    val scheme = MaterialTheme.colorScheme
    val colors = PlayerColors(palette.background, palette.onBackground, palette.onBackgroundVariant,
        scheme.primary, lerp(palette.elevated, scheme.surfaceContainerHigh, .35f), scheme.onPrimary)
    StatusBarIcons(dark = false)
    val clock = rememberPlayerPlayhead(song.videoId, isPlaying, isLoading, durationMs) {
        PlayheadSample(position.positionMs, position.sampledAtNanos, position.advancing, position.seeks)
    }
    // The new timeline accounts for the preference once, at the data boundary.
    val timeline = remember(lyrics, offset) {
        lyrics?.let { original ->
            val timed = original.any { it.timeMs > 0 || it.words.isNotEmpty() }
            LyricTimeline(original.map { line ->
            var from = 0
            val tokens = line.words.flatMap { word ->
                val start = line.text.indexOf(word.text, from).takeIf { it >= 0 } ?: from
                from = (start + word.text.length).coerceAtMost(line.text.length)
                if (word.syllables.isEmpty()) listOf(TimedToken(word.text, word.startMs + offset, word.endMs + offset, start, from))
                else word.syllables.map { part -> TimedToken(word.text, part.startMs + offset, part.endMs + offset,
                    (start + part.startChar).coerceIn(0, line.text.length), (start + part.endChar).coerceIn(0, line.text.length)) }
            }
            PlayerLyric(line.text, if (!timed || line.timeMs < 0) -1 else (line.timeMs + offset).coerceAtLeast(0), line.sungUntilMs?.plus(offset), tokens, seekMs = line.timeMs.coerceAtLeast(0))
        }) }
    }
    val tracks = remember(queue) { queue.map { PlayerTrack(it.videoId, it.title, it.artist, it.thumbnailUrl?.artworkAt(CARD_ART_PX), it.queueEntryId ?: it.videoId) } }
    val current = remember(song) { PlayerTrack(song.videoId, song.title, song.artist, remoteArt) }
    val pastRestart by remember(position) { derivedStateOf { position.positionMs > com.music.bitchord.playback.BACK_RESTARTS_AFTER_MS } }
    val session = PlayerSession(isPlaying, isLoading || audioVersionSwitching, durationMs,
        hasPrevious || pastRestart, hasNext, controlsLocked,
        likeStatus == LikeStatus.LIKE, signedIn, shuffleEnabled, repeatMode, autoplayEnabled, lyricsMissing)
    val labels = PlayerLabels(
        stringResource(Res.string.play), stringResource(Res.string.pause), stringResource(Res.string.widget_previous), stringResource(Res.string.widget_next),
        stringResource(Res.string.daylight_player_lyrics), stringResource(Res.string.queue), stringResource(Res.string.audio_output), stringResource(Res.string.daylight_player_close),
        stringResource(Res.string.like), stringResource(Res.string.more), stringResource(Res.string.daylight_player_shuffle), stringResource(Res.string.daylight_player_repeat),
        stringResource(Res.string.autoplay), stringResource(Res.string.remove_from_queue), stringResource(Res.string.clear),
        stringResource(Res.string.daylight_player_lyrics_loading), stringResource(Res.string.lyrics_not_available), stringResource(Res.string.daylight_player_resume_lyrics),
    )
    fun guarded(action: () -> Unit) { if (controlsLocked) onBlockedControl() else action() }
    val actions = PlayerActions(onPlayPause, { guarded(onPrevious) }, { guarded(onNext) },
        { guarded { onSeekFraction(it) } }, { guarded { onSeek(it.coerceAtLeast(0)) } }, onToggleLike, onOpenMenu,
        onDismiss, { artworkOpen = true }, openOutput, { guarded(onToggleShuffle) }, { guarded(onCycleRepeat) }, { guarded(onToggleAutoplay) },
        { guarded { onJumpTo(it) } }, { guarded { onRemoveFromQueue(it) } }, { guarded(onClearQueue) }, onBlockedControl)
    DaylightPlayer(current, session, clock, timeline, tracks, queueIndex, colors, labels, actions, reduced, panel, { panel = it }, onMenuAnchor,
        artwork = { mod -> DaylightArtwork(song, art, isPlaying, panel != PlayerPanel.ARTWORK || artworkOpen, mod) },
        progress = { mod, head, enabled -> DaylightProgress(song.videoId, head, durationMs, isLoading || audioVersionSwitching,
            enabled, colors, onSeekFraction, onBlockedControl, mod) { PlaybackQualityLabel(song, isLoading, Modifier.align(androidx.compose.ui.Alignment.Center), ink = colors.ink) } },
        output = { TextButton(openOutput) { Text(rememberAudioOutputName(accountName), color = colors.muted, maxLines = 1) } },
        lyricTools = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ providersOpen = true }) { Text(stringResource(Res.string.choose_lyrics_provider), color = colors.ink) }
                TextButton({ offsetOpen = true }) { Text(stringResource(Res.string.lyrics_offset), color = colors.ink) }
            }
        }, modifier = modifier.hazeSource(haze))
    if (outputOpen) AudioOutputSheet(haze, accountName, { outputOpen = false }, { pipelineOpen = true }, { castOpen = true })
    if (pipelineOpen) PlayerPlatform.host.AudioPipelineDialog(haze, isPlaying) { pipelineOpen = false }
    if (castOpen) PlayerPlatform.host.CastDialog(haze) { castOpen = false }
    if (providersOpen) LyricsProviderSheet(haze, lyricsSource, lyricsProviderStates, onSelectLyricsProvider, onDismiss = { providersOpen = false })
    if (offsetOpen || lyricsOffsetOpen) LyricsOffsetSheet(haze, { offsetOpen = false; onDismissLyricsOffset() })
    if (artworkOpen && remoteArt != null) PlayerPlatform.host.ArtworkViewer(remoteArt, song.title) { artworkOpen = false }
    KeepScreenOn(isPlaying && panel == PlayerPanel.LYRICS)
}

@Composable
private fun rememberSaveablePlayerPanel() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(PlayerPanel.ARTWORK) }

@Composable
private fun DaylightArtwork(song: Song, art: PlayerArtwork, playing: Boolean, suspended: Boolean, modifier: Modifier) {
    val clip = rememberCanvasArtwork(song)
    val handoff by rememberAutomixArtworkHandoff(art.url)
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val handoffShare = animateFloatAsState(if (handoff) 1f else 0f, tween(if (reduced) 0 else 400), label = "daylight-automix-cover")
    Box(modifier) {
        AutomixArtwork(art.request, art::onState, Modifier.fillMaxSize())
        if (clip != null) CanvasArtworkPlayer(clip, playing, Modifier.fillMaxSize(),
            presentationAlpha = { 1f - handoffShare.value }, pausedForTransition = !playing || suspended || handoff)
    }
}

@Composable
private fun DaylightProgress(trackId: String, head: PlayerPlayhead, durationMs: Long, loading: Boolean, enabled: Boolean,
    colors: PlayerColors, onSeek: (Float) -> Unit, onBlocked: () -> Unit, modifier: Modifier, quality: @Composable BoxScope.() -> Unit) {
    var drag by remember(trackId) { mutableStateOf<Float?>(null) }
    val transition by PlayerSettings.smartTransitionWindow.collectAsStateWithLifecycle()
    val blend by PlayerSettings.smartMixInProgress.collectAsStateWithLifecycle()
    Box(modifier) {
        PlayerScrubber(shown = { drag ?: if (durationMs > 0) (head.millis.toDouble() / durationMs).toFloat().coerceIn(0f, 1f) else 0f },
            durationMs = durationMs, loading = loading, mixing = blend && drag == null,
            transitionWindow = transition?.takeIf { drag == null && it.end > it.start }?.let { it.start..it.end },
            onScrub = { if (enabled) drag = it else onBlocked() },
            onScrubFinished = { drag?.let(onSeek); drag = null }, ink = colors.ink, centerLabel = quality)
    }
}
