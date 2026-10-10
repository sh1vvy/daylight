package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.PlayerNavigationBar
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Dev8DiscoverNativeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun suppliedIconAndDiscoverNavigationWorkInEveryMaterialAndTheme() {
        assumeTrue(Build.HARDWARE=="ranchu" || Build.HARDWARE=="goldfish")
        val old=AppSettings.reduceAnimation.value
        val oldBlur=AppSettings.reduceDynamicBlur.value
        val theme=mutableStateOf(ThemeMode.DARK)
        val glass=mutableStateOf(false)
        val selected=mutableIntStateOf(0)
        val connection=FloatingTabBarScrollConnection(scrollThresholdPx=30f)
        val haze=HazeState()
        val tabs=listOf(BottomTab("Home",BitChordIcons.Home),BottomTab("Discover",BitChordIcons.TabDiscover),BottomTab("Library",BitChordIcons.TabLibrary),BottomTab("Search",BitChordIcons.TabSearch))
        val backdrop=object:Backdrop {
            override val isCoordinatesDependent=false
            override fun DrawScope.drawBackdrop(density:Density,coordinates:LayoutCoordinates?,layerBlock:(GraphicsLayerScope.()->Unit)?) { drawRect(Color(0xff96838d)) }
        }
        try {
            AppSettings.reduceAnimation.value=false;AppSettings.reduceDynamicBlur.value=false
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme=theme.value==ThemeMode.DARK,pinkCloud=theme.value==ThemeMode.PINK_CLOUD) {
                        CompositionLocalProvider(LocalLiquidGlassEnabled provides glass.value,LocalAppBackdrop provides backdrop) {
                            Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).hazeSource(haze)) {
                                PlayerNavigationBar(haze,tabs,selected.intValue,{selected.intValue=it},connection,null,false,false,{},{},{},{},
                                    modifier=Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal=16.dp))
                            }
                        }
                    }
                } }
                for (t in listOf(ThemeMode.DARK,ThemeMode.LIGHT,ThemeMode.PINK_CLOUD)) for (g in listOf(false,true)) {
                    compose.runOnIdle { theme.value=t;glass.value=g;selected.intValue=0;connection.expand() }
                    compose.waitForIdle()
                    compose.onNodeWithContentDescription("Discover").assertIsDisplayed().performClick()
                    compose.runOnIdle { assertEquals(1,selected.intValue) }
                    compose.onNodeWithText("Discover").assertIsDisplayed()
                    val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    val file=File(context.getExternalFilesDir(null),"dev8-qa/discover-$t-glass-$g.png");file.parentFile!!.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                    compose.runOnIdle { connection.inline() };compose.waitForIdle()
                    compose.onNodeWithContentDescription("Discover").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Search").assertIsDisplayed().performClick()
                    compose.runOnIdle { assertEquals(3,selected.intValue) }
                }
            }
        } finally {AppSettings.reduceAnimation.value=old;AppSettings.reduceDynamicBlur.value=oldBlur}
    }
}
