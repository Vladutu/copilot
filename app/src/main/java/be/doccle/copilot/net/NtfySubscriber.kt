package be.doccle.copilot.net

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class NtfySubscriber(
    private val client: OkHttpClient = defaultClient,
    private val base: String,
    private val topic: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val maxAgeSec: Long,
    private val backoffInitialMs: Long = 1_000L,
    private val backoffMaxMs: Long = 30_000L,
) {
    fun subscribe(): Flow<Message> = flow {
        var delayMs = backoffInitialMs
        val req = Request.Builder().url("$base/$topic/json").build()

        while (currentCoroutineContext().isActive) {
            try {
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    delayMs = backoffInitialMs
                    val source = response.body!!.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val msg = Message.parseEnvelope(line, clock(), maxAgeSec)
                        if (msg != null) emit(msg)
                    }
                }
            } catch (e: IOException) {
                // expected on disconnect, sleep, wifi flap
            }
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(backoffMaxMs)
        }
    }

    companion object {
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived stream
            .build()
    }
}
