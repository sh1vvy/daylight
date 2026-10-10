package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.components.SongActionsSheet
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.MixPulse
import com.music.bitchord.ui.player.rememberMixPulse
import com.music.bitchord.ui.theme.BitChordTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real player composition, offline covers, engine-shaped states, no live account or audio changes. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class CanaryAutomixVisualNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun artworkDissolveAndActionSheetKeepTheirGeometryAndControls() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            InstrumentationRegistry.getArguments().getString("allowPhysicalPreview") == "true")
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        val motionCover=InstrumentationRegistry.getArguments().getString("motionCover") == "true"
        assumeTrue(!motionCover || Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val oldTheme=AppSettings.themeMode.value
        val oldCanvas=AppSettings.animatedCanvas.value
        val oldMotion=AppSettings.reduceAnimation.value
        val oldBleed=AppSettings.fullBleedArtwork.value
        val oldBlend=AppSettings.smartMixBlend.value
        val oldMixing=AppSettings.smartMixInProgress.value
        // Preview only: never persist test choices on an explicitly allowed physical phone.
        AppSettings.themeMode.value=theme
        AppSettings.animatedCanvas.value=motionCover
        AppSettings.reduceAnimation.value=false
        AppSettings.fullBleedArtwork.value=InstrumentationRegistry.getArguments().getString("fullBleed") == "true"
        val first=Song("qa-automix-first","Familiar favourite","Daylight QA",cover("outgoing",Color.rgb(180,70,80)),"3:00")
        val next=Song("qa-automix-next","A little discovery","Daylight QA",cover("incoming",Color.rgb(55,90,175)),"3:00")
        val current=mutableStateOf(first)
        val menu=mutableStateOf(false)
        val position=PlaybackPosition().apply { report(170_000) }
        val blend=MixBlend(500f, System.nanoTime(), false, outgoingId=first.videoId,incomingId=next.videoId,
            outgoingArtwork=first.thumbnailUrl,incomingArtwork=next.thumbnailUrl,artworkSpan=0.3f)
        var compositions=0
        var saves=0
        var playsNext=0
        val removeMotion=if(motionCover) motionCovers(listOf(first,next)) else ({})
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    SideEffect { compositions++ }
                    BitChordTheme(darkTheme=theme==ThemeMode.DARK,pinkCloud=theme==ThemeMode.PINK_CLOUD) {
                        val size=LocalConfiguration.current
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            if (menu.value) {
                                ModalBottomSheet(onDismissRequest={menu.value=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=androidx.compose.ui.graphics.Color.Transparent,dragHandle=null) {
                                SongActionsSheet(current.value, true, LikeStatus.LIKE,
                                    onPlayNext={playsNext++},onAddToQueue={},onStartRadio={},onDownload={},onToggleLike={},onToggleDislike={},
                                    onAddToPlaylist={saves++},onOpenAlbum={},onOpenArtist={},showSleepTimer=true,onLyricsOffset={},onShare={})
                                }
                            } else {
                                NowPlayingScreen(song=current.value,isPlaying=true,isLoading=false,position=position,durationMs=180_000,
                                    audioVersionSwitching=false,qualityUpgraded=false,queue=listOf(first,next),queueIndex=if(current.value==first)0 else 1,
                                    hasPrevious=false,hasNext=true,repeatMode=0,shuffleEnabled=false,autoplayEnabled=false,signedIn=true,accountName="QA",likeStatus=LikeStatus.LIKE,
                                    onToggleLike={},onPlayPause={},onNext={},onPrevious={},onBlockedControl={},onSeek={},onSeekFraction={},onToggleShuffle={},onCycleRepeat={},
                                    onToggleAutoplay={},onJumpTo={},onRemoveFromQueue={},onMoveInQueue={_,_->},onClearQueue={},onOpenMenu={menu.value=true},
                                    onOpenAlbum={},onOpenArtist={_,_->},onOpenPlaybackSource={},onListenTogether={},lyrics=emptyList(),lyricsSource=null,
                                    lyricsProviderStates=emptyMap(),onSelectLyricsProvider={},lyricsUnavailable=true,lyricsOffsetOpen=false,onDismissLyricsOffset={},
                                    windowWidth=size.screenWidthDp.dp,windowHeight=size.screenHeightDp.dp)
                            }
                        }
                    }
                } }
                compose.waitForIdle(); SystemClock.sleep(300)
                if(motionCover) assertMotionPlaying(scenario)
                val cover=compose.onNodeWithTag("automix-cover", useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                screenshot("before-$theme")
                compose.runOnIdle { AppSettings.smartMixBlend.value=blend; AppSettings.smartMixInProgress.value=true }
                compose.waitForIdle(); SystemClock.sleep(300)
                val before=colourAt(cover.center.x.toInt(),cover.center.y.toInt())
                if(!motionCover) assertTrue("Outgoing cover stays visible", Color.red(before)>Color.blue(before))
                val beforeTicks=compositions
                // Progress must not recompose the screen's root or change the sleeve's size.
                for (i in 1..8) {
                    instrumentation.runOnMainSync { AppSettings.smartMixBlend.value=blend.copy(progress=i/40f) }
                    SystemClock.sleep(32)
                }
                if(!motionCover) assertEquals(beforeTicks,compositions)
                compose.runOnIdle { AppSettings.smartMixBlend.value=blend.copy(progress=0.5f); current.value=next; position.report(12_000) }
                compose.waitForIdle(); SystemClock.sleep(if(motionCover)700 else 200)
                val handoffBounds=compose.onNodeWithTag("automix-cover", useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertTrue("The sleeve keeps its geometry",kotlin.math.abs(cover.width-handoffBounds.width)<4 && kotlin.math.abs(cover.height-handoffBounds.height)<4)
                val middle=colourAt(cover.center.x.toInt(),cover.center.y.toInt())
                assertTrue("Both covers contribute at handoff",Color.blue(middle)>100 && Color.red(middle) in 80..160)
                screenshot("handoff-$theme")
                compose.runOnIdle { AppSettings.smartMixBlend.value=blend.copy(progress=0.9f) }
                compose.waitForIdle()
                val after=colourAt(cover.center.x.toInt(),cover.center.y.toInt())
                if(motionCover) assertMotionPlaying(scenario) else assertTrue("Incoming cover completes", Color.blue(after)>Color.red(after))
                screenshot("complete-$theme")
                compose.runOnIdle { AppSettings.reduceAnimation.value=true; AppSettings.smartMixBlend.value=blend.copy(progress=0.2f) }
                compose.waitForIdle()
                val reduced=colourAt(cover.center.x.toInt(),cover.center.y.toInt())
                if(!motionCover) assertTrue("Reduced motion follows current cover immediately",Color.blue(reduced)>Color.red(reduced))
                compose.runOnIdle { AppSettings.smartMixBlend.value=null; AppSettings.smartMixInProgress.value=false; menu.value=true }
                compose.waitForIdle()
                compose.onNodeWithText(context.getString(R.string.add_to_playlist)).assertIsDisplayed().performClick()
                compose.onNodeWithText(context.getString(R.string.play_next)).assertIsDisplayed().performClick()
                assertEquals(1,saves); assertEquals(1,playsNext)
                screenshot("actions-$theme")
                compose.onNodeWithText(context.getString(R.string.share)).performScrollTo().assertIsDisplayed()
                screenshot("actions-bottom-$theme")
            }
        } finally {
            AppSettings.smartMixBlend.value=oldBlend; AppSettings.smartMixInProgress.value=oldMixing
            AppSettings.themeMode.value=oldTheme;AppSettings.animatedCanvas.value=oldCanvas
            AppSettings.reduceAnimation.value=oldMotion;AppSettings.fullBleedArtwork.value=oldBleed
            removeMotion()
        }
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun motionCovers(songs:List<Song>):()->Unit {
        val url="https://daylight.invalid/automix/${System.nanoTime()}.mp4"
        val cache=CanvasCache::class.java.getDeclaredField("cache").apply {isAccessible=true}.get(CanvasCache) as SimpleCache
        val bytes=instrumentation.context.assets.open("canary-automix-cover.mp4").use {it.readBytes()}
        val hole=requireNotNull(cache.startReadWrite(url,0,bytes.size.toLong()))
        try {
            cache.startFile(url,0,bytes.size.toLong()).apply {writeBytes(bytes)}.let {cache.commitFile(it,bytes.size.toLong())}
            cache.applyContentMetadataMutations(url,ContentMetadataMutations().also {ContentMetadataMutations.setContentLength(it,bytes.size.toLong())})
        } finally {cache.releaseHoleSpan(hole)}
        @Suppress("UNCHECKED_CAST")
        val entries=CanvasRepository::class.java.getDeclaredField("cache").apply {isAccessible=true}.get(CanvasRepository) as MutableMap<String,Any>
        val constructor=CanvasRepository::class.java.declaredClasses.first {it.simpleName=="Entry"}.declaredConstructors.single().apply {isAccessible=true}
        val prior=synchronized(entries) {songs.associate {song -> val key="song|${song.videoId}";key to entries.put(key,constructor.newInstance(CanvasArtwork(url),true))}}
        return {synchronized(entries) {prior.forEach {(key,entry)->if(entry==null)entries.remove(key) else entries[key]=entry}};cache.removeResource(url)}
    }
    private fun assertMotionPlaying(scenario:ActivityScenario<MainActivity>) {
        fun frame():Bitmap? {
            var bitmap:Bitmap?=null
            fun visit(view:View) {
                if(view is TextureView && view.isAvailable) bitmap=view.getBitmap(32,32)
                if(view is ViewGroup) repeat(view.childCount) {visit(view.getChildAt(it))}
            }
            scenario.onActivity {visit(it.window.decorView)}
            return bitmap
        }
        var first:Bitmap?=null
        val deadline=SystemClock.uptimeMillis()+6000
        while(first==null && SystemClock.uptimeMillis()<deadline) {SystemClock.sleep(100);first=frame()}
        assertNotNull("Animated cover has a decoded frame",first)
        SystemClock.sleep(450)
        val next=requireNotNull(frame())
        assertFalse("Animated cover continues playing",first!!.sameAs(next))
        first.recycle();next.recycle()
    }
    @Test fun glowBreathesOnlyWhilePlayingAndSettlesWhenPaused() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val blend=mutableStateOf<MixBlend?>(MixBlend(500f,System.nanoTime(),true))
        val motion=mutableStateOf(true)
        var pulse:MixPulse?=null
        var draws=0
        compose.mainClock.autoAdvance=false
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent {
                    pulse=rememberMixPulse({blend.value},motion.value)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        draws++
                        drawRect(androidx.compose.ui.graphics.Color.White.copy(alpha=pulse!!.alpha(1f)))
                    }
                } }
                compose.mainClock.advanceTimeBy(1200)
                compose.waitForIdle()
                val first=pulse!!.alpha(1f)
                compose.mainClock.advanceTimeBy(1800)
                compose.waitForIdle()
                assertTrue("The slow glow changes while playing",kotlin.math.abs(first-pulse!!.alpha(1f))>0.025f)
                compose.runOnIdle { blend.value=blend.value!!.copy(playing=false) }
                compose.mainClock.advanceTimeBy(1200)
                compose.waitForIdle()
                assertEquals(1f,pulse!!.alpha(1f),0.001f)
                val settled=draws
                compose.mainClock.advanceTimeBy(2000)
                compose.waitForIdle()
                assertEquals("Paused glow stops requesting frames",settled,draws)
                compose.runOnIdle { blend.value=blend.value!!.copy(playing=true);motion.value=false }
                compose.mainClock.advanceTimeBy(1200)
                compose.waitForIdle()
                assertEquals("Reduced motion holds a steady label/bar",1f,pulse!!.alpha(1f),0.001f)
            }
        } finally { compose.mainClock.autoAdvance=true }
    }
    private fun cover(name:String,colour:Int):String {
        val bitmap=Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(colour)
        val file=File(context.cacheDir,"qa-automix-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        return file.toURI().toString()
    }
    private fun colourAt(x:Int,y:Int):Int {
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val colour=bitmap.getPixel(x,y);bitmap.recycle();return colour
    }
    private fun screenshot(name:String) {
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(context.getExternalFilesDir(null),"canary-11-qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
