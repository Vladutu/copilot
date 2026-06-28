package com.vladutu.copilot.timemachine.wikipedia

import com.vladutu.copilot.timemachine.SongRef

/**
 * Pure parser for a Billboard Year-End Hot 100 wikitable (MediaWiki wikitext). Kept JVM-pure
 * and free of any HTTP/JSON types so it is unit-tested directly against captured fixtures.
 *
 * Validated against live Wikipedia for every complete year 1980–2025 (spec
 * 2026-06-28-music-time-machine). Handles: inline `||` cells, `[[target|display]]` wikilinks,
 * `<ref>` tags, `<!-- HTML comments -->`, cell attributes (`rowspan="2"|`), parenthetical artist
 * groups, and `A / B` double-A-side titles.
 */
internal object YearEndWikitextParser {

    /** Top [n] successfully-parsed rows of the first wikitable, rank #1 first. */
    fun parse(wikitext: String, n: Int): List<SongRef> {
        // Strip comments + refs from the WHOLE text first: a comment can straddle a `||`
        // cell delimiter (real case: 2021), which corrupts cell splitting if done per-cell.
        var wt = COMMENT.replace(wikitext, "")
        wt = REF.replace(wt, "")

        val start = wt.indexOf("wikitable")
        if (start < 0) return emptyList()
        val endRel = wt.indexOf("\n|}", start)
        val table = if (endRel >= 0) wt.substring(start, endRel) else wt.substring(start)

        val out = ArrayList<SongRef>(n)
        for (rawRow in table.split("\n|-")) {
            val row = rawRow.trim()
            if (row.startsWith("!")) continue            // header row
            // Normalise newline-leading-`|` (one-cell-per-line style) to inline `||`.
            val line = NEWLINE_PIPE.replace(row, " || ")
            val cells = line.split("||")
                .map { it.trim().trimStart('|').trim() }
                .filter { it.isNotEmpty() }
            if (cells.size < 3) continue                 // rowspan continuation / non-data row
            if (cells[0].none { it.isDigit() }) continue // rank cell must hold a number

            val title = trimTitle(cleanCell(cells[1]))
            // Split off "featuring …" BEFORE cleaning so the credit list never reaches the query.
            val artist = trimArtist(cleanCell(cells[2].split(FEATURING)[0]))
            if (title.isNotEmpty() && artist.isNotEmpty()) {
                out.add(SongRef(artist = artist, title = title))
                if (out.size >= n) break
            }
        }
        return out
    }

    private fun cleanCell(raw: String): String {
        var s = raw
        // Drop a leading cell-attribute prefix like `rowspan="2"|` or `style="…"|`
        // (an `=` appears before a `|`, and before any wikilink `[`).
        if (s.contains('|') && CELL_ATTR.containsMatchIn(s)) {
            s = s.substringAfter('|')
        }
        s = delink(s)
        s = ITALICS.replace(s, "")
        s = TEMPLATE.replace(s, "")
        return s.replace("\"", "").trim()
    }

    /** `[[target|display]] -> display`, `[[x]] -> x`. */
    private fun delink(s: String): String =
        LINK.replace(s) { m -> m.groupValues[1].substringAfterLast('|') }

    /** Double A-side "A / B" -> first side. */
    private fun trimTitle(s: String): String = s.substringBefore(" / ").trim()

    /** Drop a trailing parenthetical group list, e.g. "Dionne and Friends (…)" -> "Dionne and Friends". */
    private fun trimArtist(s: String): String = s.substringBefore('(').trim()

    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val REF = Regex("<ref.*?(/>|</ref>)", RegexOption.DOT_MATCHES_ALL)
    private val NEWLINE_PIPE = Regex("\\n\\|")
    private val LINK = Regex("\\[\\[([^\\]]+)\\]\\]")
    private val CELL_ATTR = Regex("^[^\\[]*?=[^\\[]*?\\|")
    private val ITALICS = Regex("''+")
    private val TEMPLATE = Regex("\\{\\{[^}]*\\}\\}")
    private val FEATURING = Regex("featuring|feat\\.", RegexOption.IGNORE_CASE)
}
