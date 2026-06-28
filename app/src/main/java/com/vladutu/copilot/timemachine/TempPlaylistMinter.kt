package com.vladutu.copilot.timemachine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Mints an anonymous YouTube temp playlist from a list of video IDs via the undocumented
 * `watch_videos` endpoint: YouTube creates a server-side "TLGG…" list and answers 303 with its
 * ID in the Location header. Redirect-following is disabled because the header IS the payload.
 *
 * Returns the raw list ID; the caller decides the playback URL (the Time Machine plays it in
 * order — no shuffle). The minted list is short-lived, which is fine: a fresh one is minted per
 * tap and used seconds later.
 */
class TempPlaylistMinter(
    okHttp: OkHttpClient,
    private val baseUrl: String = "https://www.youtube.com",
) : PlaylistMinter {

    private val client = okHttp.newBuilder().followRedirects(false).build()

    override suspend fun mint(videoIds: List<String>): String = withContext(Dispatchers.IO) {
        require(videoIds.isNotEmpty()) { "no video ids to mint" }
        val request = Request.Builder()
            .url("$baseUrl/watch_videos?video_ids=${videoIds.joinToString(",")}")
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (!response.isRedirect || location == null) {
                    throw TimeMachineException("watch_videos: expected redirect, got ${response.code}")
                }
                queryParam(location, "list")
                    ?: throw TimeMachineException("watch_videos: no list id in redirect '$location'")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeMachineException) {
            throw e
        } catch (e: Exception) {
            throw TimeMachineException("watch_videos call failed: ${e.message}", e)
        }
    }

    /** JVM-pure query-param extractor (no android.net.Uri). */
    private fun queryParam(url: String, param: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == param }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        // Desktop UA keeps YouTube on the plain redirect path (mobile UAs sometimes get interstitials).
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    }
}
