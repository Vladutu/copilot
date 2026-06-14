package com.vladutu.copilot.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeKnobTest {

    @Test fun `four tiles when nothing playing`() {
        assertEquals(4, HomeKnob.tileCount(songPlaying = false))
    }

    @Test fun `five tiles when a song is playing (heart is the last stop)`() {
        assertEquals(5, HomeKnob.tileCount(songPlaying = true))
    }

    @Test fun `focus is clamped into range`() {
        assertEquals(0, HomeKnob.clampFocus(focused = 0, tileCount = 4))
        assertEquals(3, HomeKnob.clampFocus(focused = 3, tileCount = 4))
    }

    @Test fun `when the song stops while focused on the heart, focus drops to the last tile`() {
        // heart was index 4 (count 5); song stops → count 4 → focus clamps to 3
        assertEquals(3, HomeKnob.clampFocus(focused = 4, tileCount = 4))
    }
}
