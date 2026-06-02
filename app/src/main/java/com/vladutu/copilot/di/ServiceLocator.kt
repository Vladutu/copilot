package com.vladutu.copilot.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vladutu.copilot.bubble.BubblePositionStore
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.HistoryRepository
import com.vladutu.copilot.history.HistoryStore
import okhttp3.OkHttpClient

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_history")
private val Context.bubbleDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_bubble")

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
}
