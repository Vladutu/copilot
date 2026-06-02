package com.vladutu.copilot.net

import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

data class Message(
    val v: Int,
    val ts: Long,
    val cmd: String,
    val url: String,
) {
    companion object {
        private const val SCHEMA_VERSION = 2

        private val YT_MUSIC_ALLOWED_PREFIXES = listOf(
            "https://music.youtube.com/",
        )
        private val WAZE_ALLOWED_PREFIXES = listOf(
            "https://ul.waze.com/",
            "https://waze.com/",
            "https://www.waze.com/",
        )

        fun parseEnvelope(line: String, nowSec: Long, maxAgeSec: Long): ParseResult {
            val envelope = try { JSONObject(line) } catch (e: JSONException) {
                return ParseResult.Skipped
            }
            if (envelope.optString("event") != "message") return ParseResult.Skipped
            val payload = envelope.optString("message", "")
            if (payload.isEmpty()) return ParseResult.Skipped

            val body = try { JSONObject(payload) } catch (e: JSONException) {
                return ParseResult.Rejected("malformed inner JSON")
            }

            val v = body.optInt("v", -1)
            if (v != SCHEMA_VERSION) return ParseResult.Rejected("unknown schema v=$v")

            val ts = body.optLong("ts", -1)
            if (ts < 0) return ParseResult.Rejected("missing ts")
            val skew = nowSec - ts

            if (abs(skew) > maxAgeSec) {
                return ParseResult.Rejected("stale (${skew}s)", skew)
            }

            val cmd = body.optString("cmd")
            val allowedPrefixes = when (cmd) {
                "ytmusic" -> YT_MUSIC_ALLOWED_PREFIXES
                "waze" -> WAZE_ALLOWED_PREFIXES
                else -> return ParseResult.Rejected("unknown cmd=$cmd", skew)
            }

            val url = body.optString("url")
            if (url.isBlank()) return ParseResult.Rejected("missing url", skew)
            if (allowedPrefixes.none { url.startsWith(it) }) {
                return ParseResult.Rejected("untrusted host", skew)
            }

            return ParseResult.Accepted(Message(v = v, ts = ts, cmd = cmd, url = url), skew)
        }
    }
}

sealed class ParseResult {
    object Skipped : ParseResult()
    data class Rejected(val reason: String, val skewSec: Long? = null) : ParseResult()
    data class Accepted(val message: Message, val skewSec: Long) : ParseResult()
}
