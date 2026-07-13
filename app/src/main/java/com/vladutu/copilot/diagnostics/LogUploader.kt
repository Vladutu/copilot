package com.vladutu.copilot.diagnostics

import com.vladutu.copilot.BuildConfig
import com.vladutu.copilot.settings.TopicGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads the diagnostic log to dpaste.com (anonymous, auto-deleted after [RETENTION_DAYS] days)
 * and returns the raw-text URL. The car screen shows that URL as a QR code so a user can get the
 * log onto their phone with a camera scan — no email or file-manager apps needed on the carbox.
 *
 * dpaste replaced litterbox.catbox.moe (2026-07-13) after litterbox spent a day flapping between
 * 412 "No file!", 500 and hangs. Anonymous pastes need no API key; the only ToS obligations are a
 * User-Agent header and ≤1 request/second, and Share is a manual button press.
 *
 * The returned URL gets `.txt` appended: that is dpaste's raw view, served as `text/plain`, so
 * phone browsers display the log inline instead of showing the syntax-highlighted paste page.
 */
class LogUploader(
    client: OkHttpClient,
    private val endpoint: String = UPLOAD_URL,
) {

    // Own timeout budget: free paste hosts' characteristic failure mode is slow-but-alive, and
    // OkHttp's default 10s read timeout turned those into spurious upload failures.
    private val client = client.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun upload(content: String): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("content", sanitize(scrub(content)))
            .addFormDataPart("expiry_days", RETENTION_DAYS.toString())
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "Copilot/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string().trim()
                // Success is a bare paste URL in the body; anything else is an error page.
                // Message is toasted behind an "Upload failed:" prefix — don't repeat it.
                if (!response.isSuccessful || !text.startsWith("https://")) {
                    throw IOException("HTTP ${response.code} '${text.take(120)}'")
                }
                "$text.txt"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
    }

    companion object {
        const val RETENTION_DAYS = 3
        private const val UPLOAD_URL = "https://dpaste.com/api/v2/"

        // The ntfy topic is the remote control for the car and must not survive into a public
        // paste. Call sites never log it directly, but exception stack traces can carry it
        // (OkHttp embeds the full request URL in its IOExceptions), so scrub by pattern instead
        // of trusting the log's producers.
        private val TOPIC = Regex("${TopicGenerator.PREFIX}[0-9a-f]{32}")

        fun scrub(text: String): String =
            TOPIC.replace(text, "${TopicGenerator.PREFIX}<redacted>")

        // dpaste rejects NUL bytes (HTTP 400 "Null characters are not allowed"). The log picks
        // them up when the process dies mid-append (power cut, emulator segfault): the filesystem
        // recovers the file's size but zero-fills the unflushed data. Collapse each run to one
        // visible marker — corruption right before a crash is worth seeing, not hiding.
        private val NUL_RUN = Regex("\u0000+")

        fun sanitize(text: String): String = NUL_RUN.replace(text, "�")
    }
}
