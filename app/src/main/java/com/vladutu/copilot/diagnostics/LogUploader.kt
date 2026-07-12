package com.vladutu.copilot.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Uploads the diagnostic log to litterbox.catbox.moe (anonymous, auto-deleted after 72h) and
 * returns the public file URL. The car screen shows that URL as a QR code so a user can get the
 * log onto their phone with a camera scan — no email or file-manager apps needed on the carbox.
 *
 * The file is named `.txt` deliberately: litterbox derives Content-Type from the extension, and
 * `text/plain` makes phone browsers display the log inline instead of downloading it.
 */
class LogUploader(private val client: OkHttpClient) {

    suspend fun upload(content: String): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("time", RETENTION)
            .addFormDataPart(
                "fileToUpload",
                "copilot-log.txt",
                content.toRequestBody("text/plain; charset=utf-8".toMediaType()),
            )
            .build()
        val request = Request.Builder().url(UPLOAD_URL).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string().trim()
                // Success is a bare URL in the body; anything else is an error page.
                if (!response.isSuccessful || !text.startsWith("https://")) {
                    throw IOException("upload failed: HTTP ${response.code} '${text.take(120)}'")
                }
                text
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("upload failed: ${e.message}", e)
        }
    }

    companion object {
        const val RETENTION = "72h"
        private const val UPLOAD_URL = "https://litterbox.catbox.moe/resources/internals/api.php"
    }
}
