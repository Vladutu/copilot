package com.vladutu.copilot.diagnostics

import com.vladutu.copilot.BuildConfig
import com.vladutu.copilot.settings.TopicGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads the diagnostic log to an anonymous paste host and returns a raw-text URL. The car
 * screen shows that URL as a QR code so a user can get the log onto their phone with a camera
 * scan — no email or file-manager apps needed on the carbox.
 *
 * Primary: dpaste.com (auto-deleted after [RETENTION_DAYS] days). dpaste replaced
 * litterbox.catbox.moe (2026-07-13) after litterbox spent a day flapping between 412 "No file!",
 * 500 and hangs. Anonymous pastes need no API key; the only ToS obligations are a User-Agent
 * header and ≤1 request/second, and Share is a manual button press. The returned URL gets
 * `.txt` appended: that is dpaste's raw view, served as `text/plain`.
 *
 * Fallback: paste.c-net.org (2026-10-26), tried only when dpaste fails. dpaste started serving
 * a Let's Encrypt "Gen Y" chain without the ISRG Root X1 cross-sign, which no Android trust
 * store knows yet (CertPathValidatorException on the car). c-net takes the raw body as a POST
 * and answers with a bare URL already served as text/plain; pastes live 180 days, refreshed on
 * each read.
 */
class LogUploader(
    client: OkHttpClient,
    private val dpasteEndpoint: String = DPASTE_URL,
    private val cnetEndpoint: String = CNET_URL,
) {

    // Own timeout budget: free paste hosts' characteristic failure mode is slow-but-alive, and
    // OkHttp's default 10s read timeout turned those into spurious upload failures.
    private val client = client.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun upload(content: String): String = withContext(Dispatchers.IO) {
        val text = sanitize(scrub(content))
        try {
            uploadDpaste(text).also { DiagnosticLog.i(TAG, "uploaded to dpaste: $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (primary: IOException) {
            DiagnosticLog.w(TAG, "dpaste upload failed, trying c-net: ${primary.message}")
            try {
                uploadCnet(text).also { DiagnosticLog.i(TAG, "uploaded to c-net: $it") }
            } catch (e: CancellationException) {
                throw e
            } catch (fallback: IOException) {
                // Message is toasted behind an "Upload failed:" prefix — don't repeat it.
                throw IOException("dpaste ${primary.message}; c-net ${fallback.message}", fallback)
            }
        }
    }

    private fun uploadDpaste(text: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("content", text)
            .addFormDataPart("expiry_days", RETENTION_DAYS.toString())
            .build()
        val request = Request.Builder()
            .url(dpasteEndpoint)
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()
        // Success is a bare paste URL in the body; anything else is an error page.
        return "${execute(request)}.txt"
    }

    private fun uploadCnet(text: String): String {
        val request = Request.Builder()
            .url(cnetEndpoint)
            .header("User-Agent", USER_AGENT)
            .post(text.toRequestBody(TEXT_PLAIN))
            .build()
        return execute(request)
    }

    /** Runs [request] and returns the trimmed body, which every host we use must make a URL. */
    private fun execute(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string().trim()
                if (!response.isSuccessful || !body.startsWith("https://")) {
                    throw IOException("HTTP ${response.code} '${body.take(120)}'")
                }
                return body
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
    }

    companion object {
        private const val TAG = "LogUpload"
        const val RETENTION_DAYS = 3
        private const val DPASTE_URL = "https://dpaste.com/api/v2/"
        private const val CNET_URL = "https://paste.c-net.org/"
        private const val USER_AGENT = "Copilot/${BuildConfig.VERSION_NAME}"
        private val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()

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
