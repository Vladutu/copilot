package com.vladutu.copilot.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vladutu.copilot.bubble.BubblePositionStore
import com.vladutu.copilot.discover.CategoryStore
import com.vladutu.copilot.discover.DiscoverRepository
import com.vladutu.copilot.diagnostics.LogUploader
import com.vladutu.copilot.discover.newpipe.NewPipeMusicSearcher
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.HistoryRepository
import com.vladutu.copilot.history.HistoryStore
import com.vladutu.copilot.liked.LikedSongStore
import com.vladutu.copilot.liked.LikedSongsRepository
import com.vladutu.copilot.settings.SettingsStore
import com.vladutu.copilot.timemachine.TempPlaylistMinter
import com.vladutu.copilot.timemachine.TimeMachineCache
import com.vladutu.copilot.timemachine.TimeMachineRepository
import com.vladutu.copilot.timemachine.wikipedia.WikipediaYearEndSource
import com.vladutu.copilot.update.ApkDownloader
import com.vladutu.copilot.update.ApkInstaller
import com.vladutu.copilot.update.UpdateChecker
import com.vladutu.copilot.update.UpdateManager
import com.vladutu.copilot.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_history")
private val Context.bubbleDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_bubble")
private val Context.discoverDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_discover")
private val Context.likedDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_liked")
private val Context.timeMachineDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_time_machine")
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_settings")

class ServiceLocator(private val appContext: Context, private val appScope: CoroutineScope) {
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

    val likedSongsRepository: LikedSongsRepository by lazy {
        LikedSongsRepository(LikedSongStore(appContext.likedDataStore))
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    val logUploader: LogUploader by lazy { LogUploader(okHttp) }

    val updateManager: UpdateManager by lazy {
        // Download folder must match res/xml/file_paths.xml (the FileProvider share root).
        val downloader = ApkDownloader(okHttp, File(appContext.cacheDir, "updates"))
        val installer = ApkInstaller(appContext)
        UpdateManager(
            scope = appScope,
            check = UpdateChecker(okHttp)::check,
            download = downloader::download,
            install = installer::install,
            cleanup = { withContext(Dispatchers.IO) { downloader.deleteStale(BuildConfig.VERSION_NAME) } },
        )
    }

    val discoverRepository: DiscoverRepository by lazy {
        // The only line that names the search backend (spec: containment boundary —
        // swapping to the official Data API later replaces just this constructor).
        DiscoverRepository(NewPipeMusicSearcher(okHttp))
    }

    val timeMachineRepository: TimeMachineRepository by lazy {
        // Same containment rule as discoverRepository: WikipediaYearEndSource +
        // NewPipeMusicSearcher are the only lines naming the chart/search backends.
        TimeMachineRepository(
            source = WikipediaYearEndSource(okHttp),
            searcher = NewPipeMusicSearcher(okHttp),
            cache = TimeMachineCache(appContext.timeMachineDataStore),
            minter = TempPlaylistMinter(okHttp),
        )
    }
}
