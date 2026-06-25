package com.vladutu.copilot.history

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ArtworkCache(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {
    fun fileFor(form: Form, id: String): File =
        File(File(cacheDir, "artwork"), "${form.wire}-$id.jpg")

    suspend fun download(imageUrl: String, form: Form, id: String): File? =
        withContext(Dispatchers.IO) {
            val target = fileFor(form, id)
            if (target.exists()) return@withContext target
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            return@withContext try {
                client.newCall(Request.Builder().url(imageUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    tmp.outputStream().use { sink ->
                        resp.body.byteStream().copyTo(sink)
                    }
                }
                if (!tmp.renameTo(target)) {
                    tmp.delete(); null
                } else target
            } catch (e: Exception) {
                Log.w(TAG, "artwork download failed for $imageUrl", e)
                tmp.delete()
                null
            }
        }

    private companion object { const val TAG = "ArtworkCache" }
}
