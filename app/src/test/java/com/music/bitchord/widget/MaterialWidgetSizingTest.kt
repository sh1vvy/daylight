package com.music.bitchord.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialWidgetSizingTest {
    @Test fun smallestLegacySizeKeepsUsableControlsInsteadOfTinyTouchTargets() {
        val layout = MaterialWidgetSizing.layout(110f, 110f, pill = false)
        assertEquals(MaterialWidgetLayout.COMPACT, layout)
        assertFalse(MaterialWidgetSizing.showPrevious(110f, layout))
        assertFalse(MaterialWidgetSizing.showSecondaryControls(110f, layout))
        // Play and next both retain the Android-recommended 48dp target, inside 6dp padding.
        assertTrue(110f - 12f >= 2 * 48f)
    }

    @Test fun squareExpandsToArtworkTileAndWideLayoutNeedsRoomForBothRows() {
        assertEquals(MaterialWidgetLayout.TALL, MaterialWidgetSizing.layout(170f, 220f, false))
        assertEquals(MaterialWidgetLayout.WIDE, MaterialWidgetSizing.layout(320f, 220f, false))
        assertEquals(MaterialWidgetLayout.COMPACT, MaterialWidgetSizing.layout(320f, 110f, false))
    }

    @Test fun fiveControlsAreOnlyOfferedWhenAllFive48DpTargetsFit() {
        for (layout in listOf(MaterialWidgetLayout.COMPACT, MaterialWidgetLayout.TALL, MaterialWidgetLayout.PILL)) {
            assertFalse(MaterialWidgetSizing.showSecondaryControls(250f, layout))
            assertTrue(MaterialWidgetSizing.showSecondaryControls(260f, layout))
        }
        assertTrue(260f - 20f >= 5 * 48f)
    }

    @Test fun shortLauncherRowsUseTheSlimPillWithoutClippedControls() {
        assertEquals(MaterialWidgetLayout.PILL_SLIM, MaterialWidgetSizing.layout(280f, 60f, true))
        assertEquals(MaterialWidgetLayout.PILL, MaterialWidgetSizing.layout(280f, 102f, true))
        assertEquals(MaterialWidgetLayout.PILL_SLIM, MaterialWidgetSizing.layout(180f, 102f, true))
    }

    @Test fun pillCanGrowIntoAnArtworkCardWithAllControls() {
        assertEquals(MaterialWidgetLayout.TALL, MaterialWidgetSizing.layout(280f, 220f, true))
        assertEquals(MaterialWidgetLayout.WIDE, MaterialWidgetSizing.layout(320f, 220f, true))
        assertTrue(MaterialWidgetSizing.showSecondaryControls(280f, MaterialWidgetLayout.TALL))
    }

    @Test fun slimPillKeepsTextSpaceBeforeAddingOptionalControls() {
        assertFalse(MaterialWidgetSizing.showPrevious(280f, MaterialWidgetLayout.PILL_SLIM))
        assertTrue(MaterialWidgetSizing.showPrevious(340f, MaterialWidgetLayout.PILL_SLIM))
        assertFalse(MaterialWidgetSizing.showSecondaryControls(340f, MaterialWidgetLayout.PILL_SLIM))
        assertTrue(MaterialWidgetSizing.showSecondaryControls(460f, MaterialWidgetLayout.PILL_SLIM))
    }
}
