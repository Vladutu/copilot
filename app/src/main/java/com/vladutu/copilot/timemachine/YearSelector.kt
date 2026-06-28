package com.vladutu.copilot.timemachine

import java.util.Calendar
import kotlin.random.Random

/**
 * Produces a randomly-ordered list of candidate years for one tour. The repository pulls from
 * the front, resolving each (cache or Wikipedia), and skips any that come back empty — so
 * resolution doubles as the stub/missing-year probe and no year is fetched twice.
 *
 * Range is 1980 … last complete year (device year − 1). The repository's skip-empty loop also
 * absorbs the "Jan 1, this year's page is still a stub" edge even if the clock disagrees.
 */
class YearSelector(
    private val random: Random = Random.Default,
    private val currentYear: () -> Int = { Calendar.getInstance().get(Calendar.YEAR) },
) {
    fun candidates(): List<Int> {
        val lastComplete = currentYear() - 1
        if (lastComplete < FIRST_YEAR) return emptyList()
        return (FIRST_YEAR..lastComplete).shuffled(random)
    }

    companion object {
        const val FIRST_YEAR = 1980
    }
}
