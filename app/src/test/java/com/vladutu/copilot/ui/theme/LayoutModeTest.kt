package com.vladutu.copilot.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModeTest {

    @Test fun `fromId resolves every mode by its id`() {
        LayoutMode.entries.forEach { mode ->
            assertEquals(mode, LayoutMode.fromId(mode.id))
        }
    }

    @Test fun `fromId falls back to Landscape for unknown or null ids`() {
        assertEquals(LayoutMode.LANDSCAPE, LayoutMode.fromId("square"))
        assertEquals(LayoutMode.LANDSCAPE, LayoutMode.fromId(""))
        assertEquals(LayoutMode.LANDSCAPE, LayoutMode.fromId(null))
    }

    @Test fun `mode ids are unique, non-blank, and DataStore-stable`() {
        val ids = LayoutMode.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertTrue("'$id' must be lowercase and non-blank", id.isNotBlank() && id == id.lowercase()) }
    }
}
