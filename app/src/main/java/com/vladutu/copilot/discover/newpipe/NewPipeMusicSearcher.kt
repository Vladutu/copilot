package com.vladutu.copilot.discover.newpipe

import com.vladutu.copilot.discover.FoundPlaylist
import com.vladutu.copilot.discover.FoundSong
import com.vladutu.copilot.discover.MusicSearcher
import com.vladutu.copilot.discover.SearchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * NewPipe Extractor implementation of [MusicSearcher]. Everything NewPipe stays in
 * this package (spec: containment boundary) — callers see only domain types, and all
 * failures surface as [SearchException].
 */
class NewPipeMusicSearcher(private val okHttp: OkHttpClient) : MusicSearcher {

    override suspend fun searchPlaylists(keyword: String): List<FoundPlaylist> =
        search(keyword, YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS) { items ->
            items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                val id = queryParam(item.url, "list") ?: return@mapNotNull null
                val name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FoundPlaylist(
                    playlistId = id,
                    title = name,
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url,
                )
            }
        }

    override suspend fun searchSongs(keyword: String): List<FoundSong> =
        search(keyword, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) { items ->
            items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
                val id = queryParam(item.url, "v") ?: return@mapNotNull null
                val name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FoundSong(videoId = id, title = name)
            }
        }

    private suspend fun <T> search(
        keyword: String,
        contentFilter: String,
        map: (List<InfoItem>) -> List<T>,
    ): List<T> = withContext(Dispatchers.IO) {
        try {
            ensureInit(okHttp)
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(keyword, listOf(contentFilter), "")
            val info = SearchInfo.getInfo(service, handler)
            map(info.relatedItems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SearchException("search '$keyword' failed: ${e.message}", e)
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

    private companion object {
        @Volatile private var initialized = false

        /** Lazy, idempotent NewPipe.init — keeps even init knowledge out of CopilotApp. */
        fun ensureInit(okHttp: OkHttpClient) {
            if (initialized) return
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(OkHttpDownloader(okHttp))
                    initialized = true
                }
            }
        }
    }
}
