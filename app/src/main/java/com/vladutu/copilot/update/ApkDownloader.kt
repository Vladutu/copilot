package com.vladutu.copilot.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Streams a release APK into [dir] (app cache: the installer reads it once, Android may reclaim
 * it afterwards). Writes go to a temp file and are renamed on success so a dropped connection
 * never leaves a half APK under the real name for a later tap to install.
 *
 * [onProgress] gets whole percentages (each value once, ascending, ending at 100) when the
 * server sends a Content-Length; with an unknown length it is never called.
 */
class ApkDownloader(
    client: OkHttpClient,
    private val dir: File,
) {

    // APKs are ~10 MB and car boxes are often on a phone hotspot: generous but bounded.
    private val client = client.newBuilder()
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun download(
        update: UpdateChecker.Update,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = File(dir, "copilot-${update.version}.apk")
        val partial = File(dir, "${target.name}.part")
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "Copilot/${update.version}")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val total = response.body.contentLength()
                val source = response.body.source()
                partial.sink().buffer().use { sink ->
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = source.read(sink.buffer, COPY_CHUNK)
                        if (read == -1L) break
                        sink.emitCompleteSegments()
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) {
                target.delete()
                if (!partial.renameTo(target)) throw IOException("could not move ${partial.name}")
            }
            target
        } finally {
            partial.delete()
        }
    }

    /**
     * Removes leftovers from earlier updates: every `copilot-X.Y.Z.apk` not newer than
     * [currentVersion] (the installer has its own copy once it finished) plus any `.part`
     * file (a download that did not complete). A newer APK is kept — it is the update the
     * user has not installed yet.
     */
    fun deleteStale(currentVersion: String) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val name = file.name
            val stale = when {
                name.endsWith(".part") -> true
                name.startsWith("copilot-") && name.endsWith(".apk") -> {
                    val version = name.removePrefix("copilot-").removeSuffix(".apk")
                    !UpdateChecker.isNewer(latest = version, current = currentVersion)
                }
                else -> false
            }
            if (stale) file.delete()
        }
    }

    private companion object {
        const val COPY_CHUNK = 8 * 1024L
    }
}
