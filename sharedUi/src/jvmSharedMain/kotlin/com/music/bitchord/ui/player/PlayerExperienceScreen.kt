/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-12.
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.lyrics.*
import com.music.bitchord.data.model.*
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.theme.*
import com.music.bitchord.ui.utils.containSheetGestures
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
    val reduceBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val offset by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val controlsLocked = rememberControlsLocked()
    var panel by rememberSaveablePlayerPanel()
    PlayerBackHandler(panel != PlayerPanel.ARTWORK) { panel = PlayerPanel.ARTWORK }
    val haze = remember { HazeState() }
    var outputOpen by remember { mutableStateOf(false) }
    var pipelineOpen by remember { mutableStateOf(false) }
    var castOpen by remember { mutableStateOf(false) }
    var artworkOpen by remember { mutableStateOf(false) }
    val openOutput = rememberOutputPicker { outputOpen = true }
    val remoteArt = rememberRemoteArtworkUrl(song)
    val art = rememberPlayerArtwork(remoteArt)
    val palette = rememberArtworkPalette(remoteArt, artPx = ART_PX)
    val scheme = MaterialTheme.colorScheme
    val colors = PlayerColors(palette.background, palette.onBackground, palette.onBackgroundVariant,
        scheme.primary, lerp(palette.elevated, scheme.surfaceContainerHigh, .35f), scheme.onPrimary)
    val dock = LocalPlayerDock.current?.takeIf { !reduced && windowHeight >= windowWidth }
    DisposableEffect(dock) {
        if (dock != null) { dock.attached = true; dock.surfaceAttached = true }
        onDispose { if (dock != null) { dock.surfaceAttached = false; dock.attached = false } }
    }
    val density = LocalDensity.current
    val motion = remember(dock, scheme, density) { dock?.let {
        PlayerSurfaceMotion(it::openFraction, { it.offset() ?: 0f }, it::miniSurfaceOnScreen, it::miniArtOnScreen,
            daylightFloatingColors(scheme).surface, { delta -> it.dragSurface?.invoke(delta) }, { speed -> it.releaseSurface?.invoke(speed) },
            { it.miniArtRadiusOnScreen(density) })
    } }
    val docking by remember(dock) { derivedStateOf { dock != null && dock.openFraction() < 1f } }
    StatusBarIcons(dark = false)
    val clock = rememberPlayerPlayhead(song.videoId, isPlaying, isLoading, durationMs) {
        PlayheadSample(position.positionMs, position.sampledAtNanos, position.advancing, position.seeks)
    }
    // Both styles use the established renderer, including the credited Accompanist spring trail.
    // It reads the unmodified provider timing through an offset-aware, draw-local clock.
    val lyricPlayhead = rememberLyricPlayhead(position)
    val sourceCredit = if (lyrics.isNullOrEmpty()) null else if (lyricsSource != null)
        stringResource(Res.string.lyrics_by, lyricsSource.label) else stringResource(Res.string.lyrics_saved_with_download)
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
        stringResource(Res.string.shuffle_on), stringResource(Res.string.shuffle_off),
        stringResource(Res.string.repeat_one), stringResource(Res.string.repeat_all), stringResource(Res.string.repeat_off),
        stringResource(Res.string.autoplay_on), stringResource(Res.string.autoplay_off),
        stringResource(Res.string.daylight_player_return),
    )
    fun guarded(action: () -> Unit) { if (controlsLocked) onBlockedControl() else action() }
    val actions = PlayerActions(onPlayPause, { guarded(onPrevious) }, { guarded(onNext) },
        { guarded { onSeekFraction(it) } }, { guarded { onSeek(it.coerceAtLeast(0)) } }, onToggleLike, onOpenMenu,
        onDismiss, { artworkOpen = true }, openOutput, { guarded(onToggleShuffle) }, { guarded(onCycleRepeat) }, { guarded(onToggleAutoplay) },
        { guarded { onJumpTo(it) } }, { guarded { onRemoveFromQueue(it) } }, { guarded(onClearQueue) }, onBlockedControl)
    DaylightPlayer(current, session, clock, tracks, queueIndex, colors, labels, actions, reduced, panel, { panel = it }, onMenuAnchor,
        artwork = { mod -> DaylightArtwork(song, art, isPlaying, panel != PlayerPanel.ARTWORK || artworkOpen || docking,
            { motion?.expansion?.invoke() ?: 1f }, mod) },
        progress = { mod, head, enabled -> DaylightProgress(song.videoId, head, durationMs, isLoading || audioVersionSwitching,
            enabled, colors, onSeekFraction, onBlockedControl, mod) { PlaybackQualityLabel(song, isLoading, Modifier.align(androidx.compose.ui.Alignment.Center), ink = colors.ink) } },
        output = { TextButton(openOutput) { Text(rememberAudioOutputName(accountName), color = colors.muted, maxLines = 1) } },
        lyricsContent = { mod ->
            LyricsPanel(lines = lyrics.orEmpty(), sourceCredit = sourceCredit,
                trackKey = song.videoId, playhead = lyricPlayhead,
                looking = !lyricsUnavailable, isPlaying = isPlaying && !isLoading,
                active = panel == PlayerPanel.LYRICS && !docking,
                onSeekToLine = { actions.seekMs(adjustedLyricsSeekTarget(it, offset)) },
                controlsOpen = true, onRevealControls = {}, onHideControls = {},
                modifier = mod.testTag("daylight-accompanist-lyrics"))
        }, modifier = modifier.hazeSource(haze).then(if (panel != PlayerPanel.ARTWORK) Modifier.containSheetGestures() else Modifier), motion = motion,
        queueBlurEnabled = !reduceBlur && renderEffectBlurSupported)
    if (outputOpen) AudioOutputSheet(haze, accountName, { outputOpen = false }, { pipelineOpen = true }, { castOpen = true })
    if (pipelineOpen) PlayerPlatform.host.AudioPipelineDialog(haze, isPlaying) { pipelineOpen = false }
    if (castOpen) PlayerPlatform.host.CastDialog(haze) { castOpen = false }
    if (lyricsOffsetOpen) LyricsOffsetSheet(haze, onDismissLyricsOffset)
    if (artworkOpen && remoteArt != null) PlayerPlatform.host.ArtworkViewer(remoteArt, song.title) { artworkOpen = false }
    KeepScreenOn(isPlaying && panel == PlayerPanel.LYRICS)
}

@Composable
private fun rememberSaveablePlayerPanel() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(PlayerPanel.ARTWORK) }

@Composable
private fun DaylightArtwork(song: Song, art: PlayerArtwork, playing: Boolean, suspended: Boolean, expansion: () -> Float, modifier: Modifier) {
    val clip = rememberCanvasArtwork(song)
    val handoff by rememberAutomixArtworkHandoff(art.url)
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val handoffShare = animateFloatAsState(if (handoff) 1f else 0f, tween(if (reduced) 0 else 400), label = "daylight-automix-cover")
    Box(modifier) {
        AutomixArtwork(art.request, art::onState, Modifier.fillMaxSize())
        if (clip != null) CanvasArtworkPlayer(clip, playing, Modifier.fillMaxSize(),
            presentationAlpha = { (1f - handoffShare.value) * ((expansion() - .45f) / .55f).coerceIn(0f, 1f) },
            pausedForTransition = !playing || suspended || handoff)
    }
}

@Composable
private fun DaylightProgress(trackId: String, head: PlayerPlayhead, durationMs: Long, loading: Boolean, enabled: Boolean,
    colors: PlayerColors, onSeek: (Float) -> Unit, onBlocked: () -> Unit, modifier: Modifier, quality: @Composable BoxScope.() -> Unit) {
    var drag by remember(trackId) { mutableStateOf<Float?>(null) }
    var seekPending by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(seekPending, trackId) {
        if (seekPending) {
            // Keep the scrubbed position until the engine acknowledges it; avoid a one-frame
            // snap back to the old reading while the audio seek crosses the adapter boundary.
            kotlinx.coroutines.withTimeoutOrNull(700) {
                snapshotFlow { head.millis }.collect { actual ->
                    val target = drag ?: return@collect
                    if (kotlin.math.abs(actual - (target * durationMs).toLong()) < 1_000) {
                        drag = null; seekPending = false
                    }
                }
            }
            drag = null; seekPending = false
        }
    }
    val transition by PlayerSettings.smartTransitionWindow.collectAsStateWithLifecycle()
    val blend = PlayerSettings.smartMixBlend.collectAsStateWithLifecycle()
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val pulse = rememberMixPulse({ blend.value }, enabled = !reduced)
    val shown = { drag ?: if (durationMs > 0) (head.millis.toDouble() / durationMs).toFloat().coerceIn(0f, 1f) else 0f }
    val seconds by remember(head, durationMs) { derivedStateOf { (head.millis / 1_000).coerceIn(0, durationMs.coerceAtLeast(0) / 1_000) } }
    Column(modifier) {
        DaylightTimeline(shown, enabled, loading, colors,
            transition?.takeIf { drag == null && it.end > it.start }?.let { it.start..it.end },
            { pulse.cover * (.55f + .45f * pulse.level) },
            { seekPending = false; drag = it }, { drag?.let(onSeek); seekPending = true }, { drag = null; seekPending = false })
        Box(Modifier.fillMaxWidth().offset(y = (-6).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(daylightTime(drag?.let { (it * durationMs).toLong() } ?: seconds * 1_000), color = colors.muted, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("daylight-player-elapsed"))
                Text("-" + daylightTime(durationMs - (drag?.let { (it * durationMs).toLong() } ?: seconds * 1_000)), color = colors.muted, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("daylight-player-remaining"))
            }
            Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - pulse.cover }, content = quality)
            val mixing by remember { derivedStateOf { pulse.cover > 0f } }
            if (mixing) MixingLabel(pulse, Modifier.align(Alignment.Center), colors.ink)
        }
    }
}

private fun daylightTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
