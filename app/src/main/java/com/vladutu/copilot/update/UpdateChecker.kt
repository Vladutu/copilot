package com.vladutu.copilot.update

import com.vladutu.copilot.BuildConfig
import com.vladutu.copilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Asks GitHub for the latest Copilot release and reports it when it is newer than the running
 * build. Releases are cut by scripts/release.sh, which tags `vMAJOR.MINOR.PATCH` and attaches a
 * single `copilot-MAJOR.MINOR.PATCH.apk` asset, so the latest-release endpoint is the whole
 * contract: no changelog parsing, no pre-releases.
 *
 * [check] never throws. The check runs at every app start and an outage, rate limit or captive
 * portal must not turn into a crash or a toast on the car screen — a missed update just waits
 * for the next start.
 */
class UpdateChecker(
    client: OkHttpClient,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {

    data class Update(val version: String, val apkUrl: String)

    // Short budget: Home should not wait on GitHub.
    private val client = client.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Copilot/$currentVersion")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DiagnosticLog.i(TAG, "latest release: HTTP ${response.code}")
                    return@withContext null
                }
                val release = json.decodeFromString<Release>(response.body.string())
                val latest = release.tagName.removePrefix("v")
                if (!isNewer(latest = latest, current = currentVersion)) return@withContext null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext null
                Update(version = latest, apkUrl = apk.browserDownloadUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.i(TAG, "update check failed: ${e.message}")
            null
        }
    }

    @Serializable
    private data class Release(
        @kotlinx.serialization.SerialName("tag_name") val tagName: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @kotlinx.serialization.SerialName("browser_download_url") val browserDownloadUrl: String,
    )

    companion object {
        private const val TAG = "Update"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Vladutu/copilot/releases/latest"

        /** True when [latest] is a strictly higher MAJOR.MINOR.PATCH than [current]. */
        fun isNewer(latest: String, current: String): Boolean {
            val l = parse(latest) ?: return false
            val c = parse(current) ?: return false
            return l > c
        }

        // "0.30.1-debug" -> 0.30.1; anything that is not three numbers is treated as unknown.
        private fun parse(version: String): Triple<Int, Int, Int>? {
            val parts = version.removePrefix("v").substringBefore('-').split('.')
            if (parts.size != 3) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            return Triple(nums[0], nums[1], nums[2])
        }

        private operator fun Triple<Int, Int, Int>.compareTo(other: Triple<Int, Int, Int>): Int =
            compareValuesBy(this, other, { it.first }, { it.second }, { it.third })
    }
}
