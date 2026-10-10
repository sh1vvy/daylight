package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.components.PlayerSongMenu
import com.music.bitchord.ui.components.SongActionsPresentation
import com.music.bitchord.ui.components.SongActionsSheet
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.theme.BitChordTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Dev8SongMenuNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun compactMenuOpensFromPlayerAndKeepsSecondaryActions() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            InstrumentationRegistry.getArguments().getString("allowPhysicalPreview") == "true")
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        val oldTheme=AppSettings.themeMode.value
        val oldBleed=AppSettings.fullBleedArtwork.value
        val oldMotion=AppSettings.reduceAnimation.value
        val oldCanvas=AppSettings.animatedCanvas.value
        val menu=mutableStateOf(false)
        val anchor=mutableStateOf<Rect?>(null)
        val liked=mutableStateOf(LikeStatus.INDIFFERENT)
        val signedIn=mutableStateOf(true)
        val audioVersion=mutableStateOf(true)
        val art=File(context.cacheDir,"qa-dev8-art.png")
        Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(135,58,101)); art.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle()
        }
        val song=Song("qa-dev8-song","A little daylight","Daylight QA",art.toURI().toString(),"3:00",albumId="qa-album",artistId="qa-artist")
        var skips=0;var playlists=0;var shares=0;var albums=0;var offsets=0
        try {
            AppSettings.themeMode.value=theme;AppSettings.fullBleedArtwork.value=true;AppSettings.reduceAnimation.value=false;AppSettings.animatedCanvas.value=false
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme=theme==ThemeMode.DARK,pinkCloud=theme==ThemeMode.PINK_CLOUD) {
                        val size=LocalConfiguration.current
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            // Match MainActivity's real portrait player window, rather than
                            // mounting only its content in the activity beneath the menu.
                            ModalBottomSheet(onDismissRequest={}, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
                                sheetMaxWidth=Dp.Unspecified, containerColor=androidx.compose.ui.graphics.Color.Transparent,
                                contentWindowInsets={WindowInsets(0,0,0,0)}, dragHandle=null) {
                            NowPlayingScreen(song=song,isPlaying=false,isLoading=false,position=PlaybackPosition(),durationMs=180_000,
                                audioVersionSwitching=false,qualityUpgraded=false,queue=listOf(song),queueIndex=0,
                                hasPrevious=false,hasNext=false,repeatMode=0,shuffleEnabled=false,autoplayEnabled=false,signedIn=signedIn.value,accountName="QA",likeStatus=liked.value,
                                onToggleLike={liked.value=LikeStatus.LIKE},onPlayPause={},onNext={},onPrevious={},onBlockedControl={},onSeek={},onSeekFraction={},onToggleShuffle={},onCycleRepeat={},
                                onToggleAutoplay={},onJumpTo={},onRemoveFromQueue={},onMoveInQueue={_,_->},onClearQueue={},onOpenMenu={menu.value=true},onMenuAnchor={anchor.value=it},
                                onOpenAlbum={},onOpenArtist={_,_->},onOpenPlaybackSource={},onListenTogether={},lyrics=emptyList(),lyricsSource=null,
                                lyricsProviderStates=emptyMap(),onSelectLyricsProvider={},lyricsUnavailable=true,lyricsOffsetOpen=false,onDismissLyricsOffset={},
                                windowWidth=size.screenWidthDp.dp,windowHeight=size.screenHeightDp.dp)
                            }
                            PlayerSongMenu(if(menu.value)song else null,anchor.value,{menu.value=false}) { target ->
                                SongActionsSheet(target,signedIn.value,liked.value,onPlayNext={menu.value=false},onAddToQueue={menu.value=false},onStartRadio={menu.value=false},onDownload={},
                                    onToggleLike={liked.value=LikeStatus.LIKE},onToggleDislike={liked.value=LikeStatus.DISLIKE;skips++;menu.value=false},
                                    onAddToPlaylist={playlists++;menu.value=false},onOpenAlbum={albums++;menu.value=false},onOpenArtist={},showSleepTimer=true,onLyricsOffset={offsets++;menu.value=false},
                                    onShare={shares++;menu.value=false},onCopyLog={},onUpgradeQuality={},onToggleAudioVersion={},isAudioVersion=audioVersion.value,presentation=SongActionsPresentation.PlayerMenu)
                            }
                        }
                    }
                } }
                fun open() { compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick();compose.waitForIdle() }
                compose.waitForIdle()
                val dots = compose.onNodeWithContentDescription(context.getString(R.string.more), useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                val progress = compose.onNodeWithTag("player-progress-bar").fetchSemanticsNode().boundsInRoot
                assertTrue("Menu glyph aligns with the progress bar", kotlin.math.abs(dots.right - progress.right) < 2f)
                open()
                val bounds=compose.onNodeWithTag("player-song-menu").fetchSemanticsNode().boundsInRoot
                assertTrue("Compact menu stays under 60% of screen",bounds.height<instrumentation.uiAutomation.takeScreenshot().height*0.60f)
                assertTrue("Padded from edge", bounds.left>0)
                compose.onNodeWithText(context.getString(R.string.dislike)).assertIsDisplayed()
                screenshot("main-$theme")
                compose.onNodeWithText(context.getString(R.string.song_menu_go_to)).performClick()
                compose.onNodeWithText(context.getString(R.string.open_album)).assertIsDisplayed().performClick()
                assertEquals(1,albums)
                open();compose.onNodeWithText(context.getString(R.string.song_menu_more)).performClick()
                compose.onNodeWithText(context.getString(R.string.copy_log)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.convert_to_video)).assertDoesNotExist()
                compose.onNodeWithText(context.getString(R.string.convert_to_audio)).assertDoesNotExist()
                compose.runOnIdle { audioVersion.value=false }
                compose.onNodeWithText(context.getString(R.string.convert_to_audio)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.convert_to_video)).assertDoesNotExist()
                screenshot("more-$theme")
                compose.onNodeWithText(context.getString(R.string.sleep_timer)).performClick()
                compose.onNodeWithText(context.getString(R.string.after_this_song)).assertIsDisplayed()
                compose.onAllNodesWithContentDescription(context.getString(R.string.back)).onLast().performClick()
                compose.onNodeWithText(context.getString(R.string.lyrics_offset)).performClick();assertEquals(1,offsets)
                open();compose.onNodeWithText(context.getString(R.string.add_to_playlist)).performClick();assertEquals(1,playlists)
                open();compose.onNodeWithText(context.getString(R.string.share)).performClick();assertEquals(1,shares)
                compose.runOnIdle { signedIn.value=false }
                open();compose.onNodeWithText(context.getString(R.string.dislike)).assertIsDisplayed().performClick()
                assertEquals(1,skips)
                compose.waitForIdle()
                compose.onNodeWithTag("player-song-menu").assertDoesNotExist()
            }
        } finally {
            AppSettings.themeMode.value=oldTheme;AppSettings.fullBleedArtwork.value=oldBleed;AppSettings.reduceAnimation.value=oldMotion;AppSettings.animatedCanvas.value=oldCanvas
            art.delete()
        }
    }
    private fun screenshot(name:String) {
        compose.waitForIdle();SystemClock.sleep(150)
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(context.getExternalFilesDir(null),"dev8-qa/menu-$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
