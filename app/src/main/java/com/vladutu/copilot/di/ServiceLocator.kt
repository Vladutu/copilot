package com.vladutu.copilot.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vladutu.copilot.bubble.BubblePositionStore
import com.vladutu.copilot.charts.ChartsRepository
import com.vladutu.copilot.charts.TempPlaylistMinter
import com.vladutu.copilot.charts.newpipe.NewPipeChartFetcher
import com.vladutu.copilot.discover.CategoryStore
import com.vladutu.copilot.discover.DiscoverRepository
import com.vladutu.copilot.discover.newpipe.NewPipeMusicSearcher
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.HistoryRepository
import com.vladutu.copilot.history.HistoryStore
import okhttp3.OkHttpClient

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_history")
private val Context.bubbleDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_bubble")
private val Context.discoverDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_discover")

class ServiceLocator(private val appContext: Context) {
    val okHttp: OkHttpClient by lazy { OkHttpClient() }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(HistoryStore(appContext.historyDataStore))
    }

    val artworkCache: ArtworkCache by lazy {
        ArtworkCache(okHttp, appContext.cacheDir)
    }

    val bubblePositionStore: BubblePositionStore by lazy {
        BubblePositionStore(appContext.bubbleDataStore)
    }

    val categoryStore: CategoryStore by lazy { CategoryStore(appContext.discoverDataStore) }

    val discoverRepository: DiscoverRepository by lazy {
        // The only line that names the search backend (spec: containment boundary —
        // swapping to the official Data API later replaces just this constructor).
        DiscoverRepository(NewPipeMusicSearcher(okHttp))
    }

    val chartsRepository: ChartsRepository by lazy {
        // Same containment rule as discoverRepository: the only line that names the
        // chart backend.
        ChartsRepository(NewPipeChartFetcher(okHttp), TempPlaylistMinter(okHttp))
    }
}
