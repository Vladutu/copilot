package com.vladutu.copilot.timemachine.wikipedia

import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.timemachine.SongRef
import com.vladutu.copilot.timemachine.TimeMachineException
import com.vladutu.copilot.timemachine.YearEndChartSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [YearEndChartSource] backed by Wikipedia's Billboard Year-End Hot 100 pages, fetched as
 * clean wikitext via the MediaWiki API (no HTML scraping). All MediaWiki/HTTP/JSON knowledge
 * stays in this package; failures surface as [TimeMachineException]. Parsing is delegated to
 * the pure [YearEndWikitextParser].
 */
class WikipediaYearEndSource(
    private val okHttp: OkHttpClient,
    private val apiBase: String = "https://en.wikipedia.org/w/api.php",
) : YearEndChartSource {

    override suspend fun topSongs(year: Int, n: Int): List<SongRef> = withContext(Dispatchers.IO) {
        try {
            val url = apiBase.toHttpUrl().newBuilder()
                .addQueryParameter("action", "parse")
                .addQueryParameter("page", "Billboard_Year-End_Hot_100_singles_of_$year")
                .addQueryParameter("prop", "wikitext")
                .addQueryParameter("format", "json")
                .build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val body = okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw TimeMachineException("wikipedia HTTP ${response.code} for $year")
                }
                response.body.string()
            }
            val root = json.parseToJsonElement(body).jsonObject
            // A missing page (e.g. this year's not-yet-created stub) answers with an "error"
            // object — treat as "no songs" so the selector draws another year.
            if (root.containsKey("error")) {
                DiagnosticLog.i(TAG, "year $year: no Wikipedia page yet (stub/not published)")
                return@withContext emptyList()
            }
            val wikitext = root["parse"]?.jsonObject?.get("wikitext")?.jsonObject?.get("*")
                ?.jsonPrimitive?.content
                ?: throw TimeMachineException("unexpected MediaWiki response shape for $year")
            val songs = YearEndWikitextParser.parse(wikitext, n)
            // Page present but the parser came up short → likely a table-format change. Log loudly:
            // this is the signal that the parser needs updating (vs a normal missing-page skip above).
            if (songs.size < n) {
                DiagnosticLog.w(TAG, "year $year: page present but parsed only ${songs.size}/$n songs — possible Wikipedia format change")
            }
            songs
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeMachineException) {
            throw e
        } catch (e: Exception) {
            throw TimeMachineException("year-end fetch '$year' failed: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "TimeMachine"
        val json = Json { ignoreUnknownKeys = true }

        // Wikipedia API etiquette requires a descriptive, non-generic User-Agent.
        const val USER_AGENT =
            "CopilotCarRemote/1.0 (https://github.com/Vladutu/Copilot; car music time machine)"
    }
}
