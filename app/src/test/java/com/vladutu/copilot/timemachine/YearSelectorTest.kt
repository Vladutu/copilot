package com.vladutu.copilot.timemachine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YearSelectorTest {

    @Test fun `candidates cover 1980 through last complete year`() {
        val candidates = YearSelector(random = Random(1), currentYear = { 2026 }).candidates()
        assertEquals((1980..2025).toSet(), candidates.toSet())
        assertEquals(46, candidates.size)
    }

    @Test fun `excludes the current and future years`() {
        val candidates = YearSelector(currentYear = { 2026 }).candidates()
        assertFalse(candidates.contains(2026))
        assertFalse(candidates.contains(2027))
    }

    @Test fun `same seed is deterministic`() {
        val a = YearSelector(random = Random(42), currentYear = { 2026 }).candidates()
        val b = YearSelector(random = Random(42), currentYear = { 2026 }).candidates()
        assertEquals(a, b)
    }

    @Test fun `empty when no complete year exists yet`() {
        assertTrue(YearSelector(currentYear = { 1980 }).candidates().isEmpty())
    }
}
