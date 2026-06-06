package com.vladutu.copilot.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormTest {
    @Test fun `wire values match Pilot conventions`() {
        assertEquals("playlist", Form.PLAYLIST.wire)
        assertEquals("song", Form.SONG.wire)
        assertEquals("destination", Form.DESTINATION.wire)
    }

    @Test fun `fromWire round-trips`() {
        assertEquals(Form.PLAYLIST, Form.fromWire("playlist"))
        assertEquals(Form.SONG, Form.fromWire("song"))
        assertEquals(Form.DESTINATION, Form.fromWire("destination"))
        assertNull(Form.fromWire("unknown"))
        assertNull(Form.fromWire(null))
    }

    @Test fun `radio wire value`() {
        assertEquals("radio", Form.RADIO.wire)
    }

    @Test fun `fromWire maps radio`() {
        assertEquals(Form.RADIO, Form.fromWire("radio"))
    }
}
