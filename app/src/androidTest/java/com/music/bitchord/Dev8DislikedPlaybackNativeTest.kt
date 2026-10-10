package com.music.bitchord

import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.library.DislikedTracks
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.skipDislikedSong
import com.music.bitchord.playback.toMediaItem
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the actual service/ExoPlayer on a disposable, signed-out emulator. */
@RunWith(AndroidJUnit4::class)
class Dev8DislikedPlaybackNativeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun dislikeSkipsAndPersistsButExplicitSelectionPlaysOnce() {
        assumeTrue(Build.HARDWARE=="ranchu" || Build.HARDWARE=="goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldAutoplay=AppSettings.autoplay.value
        val oldMix=AppSettings.smartFadeEnabled.value
        val disk=DislikedTracks(context)
        val file=File(context.cacheDir,"dev8-silence.wav")
        val bytes=2*8000*2
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36+bytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes).array()
        file.outputStream().use { it.write(header);it.write(ByteArray(bytes)) }
        var controller:MediaController?=null
        val latch=CountDownLatch(1)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            AppSettings.autoplay.value=false;AppSettings.smartFadeEnabled.value=false
            LikeState.installStorage(disk,"dev8-qa")
            onMain {
                val future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
                future.addListener({controller=future.get();latch.countDown()},{it.run()})
            }
            assertTrue(latch.await(10,TimeUnit.SECONDS))
            fun song(id:String,tier:QueueTier=QueueTier.CONTEXT)=Song("local:$id",id,"QA",null,localUri=file.toURI().toString(),queueTier=tier)
            onMain {
                LikeState.set("local:bad",LikeStatus.DISLIKE)
                controller!!.setMediaItems(listOf(song("bad"),song("good"),song("bad"),song("next")).map { it.toMediaItem() },1,0)
                controller!!.prepare()
            }
            waitUntil { onMain { controller!!.mediaItemCount==2 && controller!!.currentMediaItem?.mediaId=="local:good" } }
            onMain {
                LikeState.set("local:good",LikeStatus.DISLIKE)
                controller!!.skipDislikedSong("local:good")
            }
            waitUntil { onMain { controller!!.currentMediaItem?.mediaId=="local:next" } }
            assertFalse(onMain { (0 until controller!!.mediaItemCount).any { controller!!.getMediaItemAt(it).mediaId=="local:good" } })
            LikeState.installStorage(disk,"dev8-qa")
            assertTrue(LikeState.isDisliked("local:good"))
            onMain {
                controller!!.setMediaItems(listOf(song("good").toMediaItem()),0,0)
                controller!!.repeatMode=Player.REPEAT_MODE_ONE
                controller!!.prepare();controller!!.play()
            }
            waitUntil { onMain { controller!!.isPlaying && controller!!.currentPosition>100 } }
            waitUntil { onMain { controller!!.mediaItemCount==0 } }
            // An explicit Play next request is also allowed, even for a disliked track.
            onMain {
                controller!!.repeatMode=Player.REPEAT_MODE_OFF
                controller!!.setMediaItems(listOf(song("next"),song("bad",QueueTier.USER_QUEUE)).map { it.toMediaItem() },0,0)
                controller!!.prepare();controller!!.play()
            }
            waitUntil { onMain { controller!!.currentMediaItem?.mediaId=="local:bad" && controller!!.isPlaying } }
            // Rejecting the last remaining item stops rather than leaving it playing.
            onMain { controller!!.skipDislikedSong("local:bad") }
            waitUntil { onMain { !controller!!.isPlaying } }
        } finally {
            onMain { controller?.stop();controller?.clearMediaItems();controller?.release() }
            disk.write("dev8-qa",emptySet());LikeState.installStorage(disk,null)
            AppSettings.autoplay.value=oldAutoplay;AppSettings.smartFadeEnabled.value=oldMix
            file.delete();scenario.close()
        }
    }
    private fun waitUntil(predicate:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+10_000
        while(!predicate() && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(30)
        assertTrue(predicate())
    }
    private fun <T> onMain(block:()->T):T { var result:T?=null;instrumentation.runOnMainSync {result=block()};@Suppress("UNCHECKED_CAST") return result as T }
}
