package com.vladutu.copilot.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {

    @Test fun `themeById resolves every registered theme by its id`() {
        AllThemes.forEach { spec ->
            assertEquals(spec, themeById(spec.id))
        }
    }

    @Test fun `themeById falls back to Default for unknown or null ids`() {
        assertEquals(DefaultTheme, themeById("audi"))
        assertEquals(DefaultTheme, themeById(""))
        assertEquals(DefaultTheme, themeById(null))
    }

    @Test fun `theme ids are unique, non-blank, and DataStore-stable`() {
        val ids = AllThemes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertTrue("'$id' must be lowercase and non-blank", id.isNotBlank() && id == id.lowercase()) }
    }

    @Test fun `default theme has no accents, BMW has the three tricolor stripes`() {
        assertNull(DefaultTheme.accents.stripes)
        assertEquals(3, BmwTheme.accents.stripes?.size)
    }

    @Test fun `default theme keeps today's look byte-for-byte`() {
        assertEquals(PilotTypography, DefaultTheme.typography)
        assertEquals(PilotPrimary, DefaultTheme.colorScheme.primary)
        assertEquals(PilotBackground, DefaultTheme.colorScheme.background)
    }
}
