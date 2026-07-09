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

    @Test fun `enabled - nav target already in a pane is delivered plain (waze exits splits on deep links)`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = YTM)
        assertFalse(SplitScreen.launchAdjacent(WAZE))
    }

    @Test fun `enabled - target owning the focused pane is not adjacent (double-Waze guard)`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(WAZE, YTM), focused = WAZE)
        assertFalse(SplitScreen.launchAdjacent(WAZE))
    }

    // pairingPartnerFor (two-step split build from inside Copilot)

    @Test fun `pairs a music launch with the last seen nav app when nothing else is on screen`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        SplitScreen.onWindows(visible = setOf(COPILOT), focused = COPILOT)
        assertEquals(WAZE, SplitScreen.pairingPartnerFor(YTM))
    }

    @Test fun `no pairing when a partner pane is already visible (single adjacent launch suffices)`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        SplitScreen.onWindows(visible = setOf(WAZE), focused = WAZE)
        assertNull(SplitScreen.pairingPartnerFor(YTM))
    }

    @Test fun `nav target pairs with the last music app (destination while a song plays)`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        assertEquals(YTM, SplitScreen.pairingPartnerFor(WAZE))
    }

    @Test fun `nav target without a music sighting does not pair`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(WAZE)
        assertNull(SplitScreen.pairingPartnerFor(MAPS))
    }

    @Test fun `no pairing before any nav app was seen`() {
        SplitScreen.enabled = true
        assertNull(SplitScreen.pairingPartnerFor(YTM))
    }

    @Test fun `no pairing when disabled`() {
        SplitScreen.onForeground(WAZE)
        assertNull(SplitScreen.pairingPartnerFor(YTM))
    }

    @Test fun `music foregrounds do not become the nav partner`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(YTM)
        assertNull(SplitScreen.pairingPartnerFor(SplitScreen.SOUNDCLOUD_PKG))
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
