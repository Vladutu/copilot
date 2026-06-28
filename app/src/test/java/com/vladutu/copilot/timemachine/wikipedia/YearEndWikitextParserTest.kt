package com.vladutu.copilot.timemachine.wikipedia

import com.vladutu.copilot.timemachine.SongRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are trimmed-down captures of real Billboard Year-End Hot 100 wikitext, one per edge
 * case the full 1980–2025 sweep surfaced (spec 2026-06-28-music-time-machine).
 */
class YearEndWikitextParserTest {

    /** Wraps data rows in a minimal sortable wikitable with a header row. */
    private fun table(vararg rows: String): String =
        buildString {
            append("{| class=\"wikitable sortable\" style=\"text-align: center\"\n")
            append("|-\n! No. !! Title !! Artist(s)\n")
            for (r in rows) append("|-\n").append(r).append("\n")
            append("|}\n")
        }

    @Test fun `clean inline rows (2003)`() {
        val wt = table(
            "|1 || \"[[In da Club]]\" || [[50 Cent]]",
            "|2 || \"[[Ignition (Remix)]]\" || [[R. Kelly]]",
            "|3 || \"[[Get Busy]]\" || [[Sean Paul]]",
            "|4 || \"[[Crazy in Love]]\" || [[Beyoncé]] featuring [[Jay-Z]]",
        )
        assertEquals(
            listOf(
                SongRef("50 Cent", "In da Club"),
                SongRef("R. Kelly", "Ignition (Remix)"),  // title parens kept; only ' / ' is trimmed
                SongRef("Sean Paul", "Get Busy"),
            ),
            YearEndWikitextParser.parse(wt, 3),
        )
    }

    @Test fun `featuring credit is dropped from the artist (2003 number 4)`() {
        val wt = table("|1 || \"[[Crazy in Love]]\" || [[Beyoncé]] featuring [[Jay-Z]]")
        assertEquals(listOf(SongRef("Beyoncé", "Crazy in Love")), YearEndWikitextParser.parse(wt, 1))
    }

    @Test fun `rowspan continuation row is skipped, next rank taken (1995)`() {
        val wt = table(
            "|1 || \"[[Gangsta's Paradise]]\" || [[Coolio]] featuring [[L.V. (singer)|L.V.]]",
            "|2 || \"[[Waterfalls (TLC song)|Waterfalls]]\" || rowspan=\"2\"| [[TLC (band)|TLC]]",
            "|3 || \"[[Creep (TLC song)|Creep]]\"",   // 2 cells: artist inherited via rowspan
            "|4 || \"[[Kiss from a Rose]]\" || [[Seal (musician)|Seal]]",
        )
        assertEquals(
            listOf(
                SongRef("Coolio", "Gangsta's Paradise"),
                SongRef("TLC", "Waterfalls"),
                SongRef("Seal", "Kiss from a Rose"),
            ),
            YearEndWikitextParser.parse(wt, 3),
        )
    }

    @Test fun `HTML comment straddling a cell delimiter is stripped (2021)`() {
        val wt = table(
            "|1 || \"[[Levitating (song)|Levitating]]\" || [[Dua Lipa]]<!-- editors: do not add || feat artists here -->",
        )
        assertEquals(listOf(SongRef("Dua Lipa", "Levitating")), YearEndWikitextParser.parse(wt, 1))
    }

    @Test fun `parenthetical artist group list is trimmed (1986)`() {
        val wt = table(
            "|1 || \"[[That's What Friends Are For]]\" || [[Dionne Warwick|Dionne and Friends]] (Dionne Warwick, Gladys Knight, Elton John and Stevie Wonder)",
        )
        assertEquals(
            listOf(SongRef("Dionne and Friends", "That's What Friends Are For")),
            YearEndWikitextParser.parse(wt, 1),
        )
    }

    @Test fun `double A-side title keeps only the first side (1997)`() {
        val wt = table(
            "|1 || \"[[Candle in the Wind 1997]]\" / \"[[Something About the Way You Look Tonight]]\" || [[Elton John]]",
        )
        assertEquals(
            listOf(SongRef("Elton John", "Candle in the Wind 1997")),
            YearEndWikitextParser.parse(wt, 1),
        )
    }

    @Test fun `one-cell-per-line row style is parsed`() {
        val wt = table("| 1\n| \"[[Hello]]\"\n| [[Adele]]")
        assertEquals(listOf(SongRef("Adele", "Hello")), YearEndWikitextParser.parse(wt, 1))
    }

    @Test fun `caps at n`() {
        val wt = table(
            "|1 || \"[[A]]\" || [[X]]",
            "|2 || \"[[B]]\" || [[Y]]",
            "|3 || \"[[C]]\" || [[Z]]",
        )
        assertEquals(2, YearEndWikitextParser.parse(wt, 2).size)
    }

    @Test fun `no table yields empty (stub year)`() {
        assertTrue(YearEndWikitextParser.parse("== See also ==\n* [[2027 in music]]\n", 3).isEmpty())
    }
}
