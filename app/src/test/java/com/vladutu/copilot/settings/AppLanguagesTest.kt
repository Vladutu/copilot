package com.vladutu.copilot.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguagesTest {

    @Test fun `default id is english and leads the options`() {
        assertEquals("en", AppLanguages.DEFAULT_ID)
        assertEquals(AppLanguages.DEFAULT_ID, AppLanguages.options.first().first)
    }

    @Test fun `every option id resolves to its own label`() {
        AppLanguages.options.forEach { (id, labelRes) ->
            assertEquals(labelRes, AppLanguages.labelResFor(id))
        }
    }

    @Test fun `labelResFor falls back to the first option for unknown ids`() {
        assertEquals(AppLanguages.options.first().second, AppLanguages.labelResFor("xx"))
    }

    @Test fun `localeFor maps ids to locales, unknown falls back to english`() {
        assertEquals(Locale.ENGLISH, AppLanguages.localeFor("en"))
        assertEquals(Locale.forLanguageTag("ro-RO"), AppLanguages.localeFor("ro"))
        assertEquals(Locale.ENGLISH, AppLanguages.localeFor("xx"))
    }

    @Test fun `option ids are unique, non-blank, and DataStore-stable`() {
        val ids = AppLanguages.options.map { it.first }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertEquals(id, id.lowercase().trim()) }
    }
}
