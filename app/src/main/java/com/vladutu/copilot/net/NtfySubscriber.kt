package com.vladutu.copilot.net

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * What the subscribe loop emits: stream lifecycle plus parsed payloads.
 * [Opened]/[Dropped] exist so the connection status can be derived from the
 * stream itself — real messages are rare (days apart), so "did a message
 * arrive" is useless as a health signal.
 */
sealed class StreamEvent {
    /** HTTP subscribe succeeded; the stream is live. */
    object Opened : StreamEvent()

    /** Stream ended or failed; the loop is about to back off and reconnect. */
    object Dropped : StreamEvent()

    data class Payload(val result: ParseResult) : StreamEvent()
}

class NtfySubscriber(
    private val client: OkHttpClient = defaultClient,
    private val base: String,
    private val topic: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val maxAgeSec: Long,
    private val backoffInitialMs: Long = 1_000L,
    private val backoffMaxMs: Long = 30_000L,
) {
    fun subscribe(): Flow<StreamEvent> = flow {
        var delayMs = backoffInitialMs
        val req = Request.Builder().url("$base/$topic/json").build()

        while (currentCoroutineContext().isActive) {
            try {
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    emit(StreamEvent.Opened)
                    delayMs = backoffInitialMs
                    val source = response.body.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val result = Message.parseEnvelope(line, clock(), maxAgeSec)
                        // Skipped == purely uninteresting noise (keepalive, malformed envelope).
                        // Rejected + Accepted both go upstream so the status screen can show them.
                        if (result !is ParseResult.Skipped) emit(StreamEvent.Payload(result))
                    }
                }
            } catch (e: IOException) {
                // expected on disconnect, sleep, wifi flap
            }
            emit(StreamEvent.Dropped)
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(backoffMaxMs)
        }
    }

    companion object {
        // ntfy sends a keepalive roughly every 45s. A connection that dies silently
        // (mobile data dropping with no FIN) would hang a 0-timeout read forever and
        // leave the status stuck on Connected; 90s = two missed keepalives, then the
        // read throws and the loop reports Dropped + reconnects.
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
