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
 */
class ApkDownloader(
    client: OkHttpClient,
    private val dir: File,
) {

    // APKs are ~10 MB and car boxes are often on a phone hotspot: generous but bounded.
    private val client = client.newBuilder()
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun download(update: UpdateChecker.Update): File = withContext(Dispatchers.IO) {
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
                partial.sink().buffer().use { sink -> sink.writeAll(response.body.source()) }
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
}
