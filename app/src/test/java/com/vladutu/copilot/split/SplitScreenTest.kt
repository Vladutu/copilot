package com.vladutu.copilot.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplitScreenTest {

    @Before fun setUp() {
        SplitScreen.reset()
        SplitScreen.ownPackage = COPILOT
    }

    // launchAdjacent

    @Test fun `disabled - never adjacent even with a visible partner`() {
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertFalse(SplitScreen.launchAdjacent(YTM))
    }

    @Test fun `enabled - visible partner allows adjacent`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertTrue(SplitScreen.launchAdjacent(YTM))
    }

    @Test fun `enabled - only copilot on screen is not a partner (never split with copilot)`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(COPILOT), focused = COPILOT)
        assertFalse(SplitScreen.launchAdjacent(WAZE))
    }

    @Test fun `enabled - nothing on screen is not adjacent`() {
        SplitScreen.enabled = true
        assertFalse(SplitScreen.launchAdjacent(WAZE))
    }

    @Test fun `enabled - copilot itself is never adjacent`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertFalse(SplitScreen.launchAdjacent(COPILOT))
    }

    @Test fun `enabled - music target in the unfocused pane launches adjacent (reuses its pane)`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = WAZE)
        assertTrue(SplitScreen.launchAdjacent(YTM))
    }

    @Test fun `enabled - nav app in the unfocused pane launches adjacent (launch intents reuse the pane)`() {
        // Destination deep links never consult this policy (dispatch delivers them plain
        // and arms SplitRepair); launch-intent launches (openWazeApp, switch-back) may
        // reuse the existing pane in place.
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = YTM)
        assertTrue(SplitScreen.launchAdjacent(WAZE))
    }

    @Test fun `enabled - target owning the focused pane is not adjacent (double-Waze guard)`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = WAZE)
        assertFalse(SplitScreen.launchAdjacent(WAZE))
    }

    // repairNavFor (the nav app to rebuild the split around after a plain music delivery)

    @Test fun `music target repairs around the last seen nav app`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        SplitScreen.onWindows(visible = setOf(COPILOT), focused = COPILOT)
        assertEquals(WAZE, SplitScreen.repairNavFor(YTM))
    }

    @Test fun `visible nav app without a live split repairs too (song-while-navigating)`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertEquals(WAZE, SplitScreen.repairNavFor(YTM))
    }

    @Test fun `no nav repair when a split is already live (pane delivery suffices)`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        SplitScreen.onWindows(visible = setOf(WAZE, SplitScreen.SOUNDCLOUD_PKG), focused = WAZE)
        assertNull(SplitScreen.repairNavFor(YTM))
    }

    @Test fun `prefers the visible nav app over the remembered one`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        SplitScreen.onWindows(visible = setOf(MAPS), focused = MAPS)
        assertEquals(MAPS, SplitScreen.repairNavFor(YTM))
    }

    @Test fun `nav targets never take the music-side repair path`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        assertNull(SplitScreen.repairNavFor(WAZE))
    }

    @Test fun `no nav repair before any nav app was seen`() {
        SplitScreen.enabled = true
        assertNull(SplitScreen.repairNavFor(YTM))
    }

    @Test fun `no nav repair when disabled`() {
        SplitScreen.onForeground(WAZE)
        assertNull(SplitScreen.repairNavFor(YTM))
    }

    @Test fun `music foregrounds do not become the nav side`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        assertNull(SplitScreen.repairNavFor(SplitScreen.SOUNDCLOUD_PKG))
    }

    // repairMusicFor (the music app to re-attach after a destination delivery)

    @Test fun `nav target repairs with the last music app (destination while a song plays)`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        assertEquals(YTM, SplitScreen.repairMusicFor(WAZE))
    }

    @Test fun `nav target without a music sighting has nothing to repair`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        assertNull(SplitScreen.repairMusicFor(MAPS))
    }

    @Test fun `no music repair when disabled`() {
        SplitScreen.onForeground(YTM)
        assertNull(SplitScreen.repairMusicFor(WAZE))
    }

    @Test fun `music targets never take the nav-side repair path`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        assertNull(SplitScreen.repairMusicFor(YTM))
    }

    @Test fun `music repair is armed even with a music pane visible (it will not survive the deep link)`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = YTM)
        assertEquals(YTM, SplitScreen.repairMusicFor(WAZE))
    }

    // window snapshot

    @Test fun `split is active only with two real apps on screen - copilot does not count`() {
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertFalse(SplitScreen.isSplitActive())
        SplitScreen.onWindows(visible = setOf(COPILOT, WAZE), focused = WAZE)
        assertFalse(SplitScreen.isSplitActive())
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = WAZE)
        assertTrue(SplitScreen.isSplitActive())
    }

    @Test fun `sole foreground - focused alone, copilot windows do not count`() {
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertTrue(SplitScreen.isSoleForeground(WAZE))
        SplitScreen.onWindows(visible = setOf(COPILOT, WAZE), focused = WAZE)
        assertTrue(SplitScreen.isSoleForeground(WAZE))
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = WAZE)
        assertFalse(SplitScreen.isSoleForeground(WAZE))
        SplitScreen.onWindows(visible = setOf(WAZE), focused = COPILOT)
        assertFalse(SplitScreen.isSoleForeground(WAZE))
    }

    @Test fun `isVisible tracks the latest window snapshot`() {
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = WAZE)
        assertTrue(SplitScreen.isVisible(WAZE))
        SplitScreen.onWindows(visible = setOf(YTM), focused = YTM)
        assertFalse(SplitScreen.isVisible(WAZE))
    }

    private companion object {
        const val COPILOT = "com.vladutu.copilot"
        const val WAZE = SplitScreen.WAZE_PKG
        const val MAPS = SplitScreen.MAPS_PKG
        const val YTM = SplitScreen.YT_MUSIC_PKG
    }
}
