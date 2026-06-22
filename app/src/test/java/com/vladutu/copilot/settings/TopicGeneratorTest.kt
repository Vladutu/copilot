package com.vladutu.copilot.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class TopicGeneratorTest {

    @Test fun `generate matches the topic regex`() {
        val topic = TopicGenerator.generate()
        assertTrue("'$topic' should match ^copilot-[0-9a-f]{32}\$", TopicGenerator.REGEX.matches(topic))
    }

    @Test fun `generate produces copilot prefix and 32 hex chars`() {
        val topic = TopicGenerator.generate()
        assertTrue(topic.startsWith("copilot-"))
        val hex = topic.removePrefix("copilot-")
        assertEquals(32, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun `generate is deterministic for a seeded SecureRandom`() {
        // Two SecureRandoms seeded identically must yield the same 16 bytes → same topic.
        // Pin SHA1PRNG: the platform-default provider (e.g. NativePRNG on macOS) treats
        // setSeed as supplementary entropy, so two default instances diverge. SHA1PRNG,
        // when seeded before its first draw, is fully deterministic across JVMs.
        val a = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(byteArrayOf(1, 2, 3, 4)) }
        val b = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(byteArrayOf(1, 2, 3, 4)) }
        assertEquals(TopicGenerator.generate(a), TopicGenerator.generate(b))
    }

    @Test fun `generate is overwhelmingly unique across calls`() {
        val set = (1..200).map { TopicGenerator.generate() }.toSet()
        assertEquals(200, set.size)
    }

    @Test fun `isValid accepts a generated topic`() {
        assertTrue(TopicGenerator.isValid(TopicGenerator.generate()))
    }

    @Test fun `isValid rejects null empty wrong-prefix wrong-length and uppercase`() {
        assertFalse(TopicGenerator.isValid(null))
        assertFalse(TopicGenerator.isValid(""))
        assertFalse(TopicGenerator.isValid("pilot-0123456789abcdef0123456789abcdef"))
        assertFalse(TopicGenerator.isValid("copilot-0123"))
        assertFalse(TopicGenerator.isValid("copilot-0123456789ABCDEF0123456789abcdef"))
        assertFalse(TopicGenerator.isValid("copilot-0123456789abcdef0123456789abcdefgh"))
    }
}
