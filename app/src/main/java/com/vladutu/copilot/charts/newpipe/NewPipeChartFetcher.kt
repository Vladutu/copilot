package com.vladutu.copilot.charts.newpipe

import com.vladutu.copilot.charts.ChartFetcher
import com.vladutu.copilot.charts.ChartsException
import com.vladutu.copilot.discover.newpipe.NewPipeMusicSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * NewPipe Extractor implementation of [ChartFetcher]. Everything NewPipe stays in
 * this package (spec: containment boundary) — all failures surface as [ChartsException].
 * The first response page (~100 items) covers a full chart; no pagination needed.
 */
class NewPipeChartFetcher(private val okHttp: OkHttpClient) : ChartFetcher {

    override suspend fun fetchVideoIds(playlistUrl: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                NewPipeMusicSearcher.ensureInit(okHttp)
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl)
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { queryParam(it.url, "v") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ChartsException("chart fetch '$playlistUrl' failed: ${e.message}", e)
            }
        }

    /** Tiny query-param extractor; avoids android.net.Uri so this class stays JVM-pure. */
    private fun queryParam(url: String?, param: String): String? {
        val query = url?.substringAfter('?', "") ?: return null
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == param }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }
    }
}
