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

    @Test fun `generate is deterministic for a seeded random source`() {
        // generate() must be a pure function of the bytes it draws. Verify with a
        // SecureRandom whose nextBytes is fed by java.util.Random: that algorithm is
        // JLS-specified and identical on every JVM. (SHA1PRNG's setSeed determinism is
        // provider-dependent — it passed on macOS but diverged on the CI JDK.)
        assertEquals(TopicGenerator.generate(SeededRandom(1234)), TopicGenerator.generate(SeededRandom(1234)))
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

/**
 * A deterministic [SecureRandom] for tests: its [nextBytes] is fed from [java.util.Random],
 * whose generation algorithm is JLS-specified and therefore byte-identical on every JVM —
 * unlike SHA1PRNG, whose seed handling depends on the active security provider.
 */
private class SeededRandom(seed: Long) : SecureRandom() {
    private val src = java.util.Random(seed)
    override fun nextBytes(bytes: ByteArray) {
        for (i in bytes.indices) bytes[i] = src.nextInt().toByte()
    }
}
