package com.vladutu.copilot.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceLanguagesTest {

    @Test fun `system id maps to no language tag`() {
        assertNull(VoiceLanguages.tagFor(VoiceLanguages.SYSTEM_ID))
    }

    @Test fun `language ids pass through as tags`() {
        assertEquals("ro-RO", VoiceLanguages.tagFor("ro-RO"))
    }

    @Test fun `labelResFor falls back to the first option for unknown ids`() {
        assertEquals(VoiceLanguages.options.first().second, VoiceLanguages.labelResFor("xx-XX"))
    }

    @Test fun `every option id has a label of its own`() {
        VoiceLanguages.options.forEach { (id, labelRes) ->
            assertEquals(labelRes, VoiceLanguages.labelResFor(id))
        }
    }
}
