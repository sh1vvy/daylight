package com.music.bitchord

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.ui.MainViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opt in on an already signed-in phone; read-only, never create/rename/add/rate. */
@RunWith(AndroidJUnit4::class)
class CanaryEditablePlaylistsLiveTest {
    @Test fun providerSaveDialogReturnsOwnEditablePlaylists() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowLiveReadOnly") == "true")
        val context=instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assertTrue("Use the phone's existing account",AuthStore(context).sessions.isNotEmpty())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model:MainViewModel
            scenario.onActivity { model=ViewModelProvider(it)[MainViewModel::class.java] }
            val deadline=SystemClock.uptimeMillis()+15_000
            while(!model.signedIn.value && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(100)
            assertTrue("Existing sign-in is ready",model.signedIn.value)
            val parsed=runBlocking { InnertubeParser.parseEditablePlaylistOptions(Innertube.playlistOptions("u9raS7-NisU")) }
            assertNotNull("Recognize the live provider's writable playlist dialog",parsed)
            assertTrue("The account has editable playlists",parsed!!.isNotEmpty())
            assertTrue(parsed.all { it.playlistId !in listOf("LM","WL") })
            // Verify the endpoint's choices against owner-only affordances, without logging identities.
            val ownership=runBlocking { parsed.take(3).map { InnertubeParser.parsePlaylistOwned(Innertube.browse(it.browseId)) } }
            assertTrue("Choices are owned, not just saved",ownership.all { it==true })
            val report=File(context.getExternalFilesDir(null),"canary-11-qa/playlist-options.txt")
            report.parentFile!!.mkdirs();report.writeText("Live editable dialog recognized; ${parsed.size} writable choices; ${ownership.size} ownership checks passed. No account changes.\n")
        }
    }
}
