package com.vladutu.copilot.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingUriTest {

    @Test fun `forTopic builds the pilot pair URI`() {
        val topic = "copilot-0123456789abcdef0123456789abcdef"
        assertEquals("pilot://pair?topic=$topic", PairingUri.forTopic(topic))
    }

    @Test fun `forTopic round-trips a freshly generated topic`() {
        val topic = TopicGenerator.generate()
        val uri = PairingUri.forTopic(topic)
        assertEquals("pilot://pair?topic=", uri.substringBefore(topic))
        assertEquals(topic, uri.substringAfter("topic="))
    }
}
