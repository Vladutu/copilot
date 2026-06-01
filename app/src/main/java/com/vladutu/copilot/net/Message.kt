package com.vladutu.copilot.net

import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

data class Message(
    val v: Int,
    val ts: Long,
    val cmd: String,
    val form: String = "",
    val id: String = "",
    val url: String = "",
) {
    companion object {
        private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")
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
            if (v != 1) return ParseResult.Rejected("unknown schema v=$v")

            val ts = body.optLong("ts", -1)
            if (ts < 0) return ParseResult.Rejected("missing ts")
            val skew = nowSec - ts

            if (abs(skew) > maxAgeSec) {
                return ParseResult.Rejected("stale (${skew}s)", skew)
            }

            return when (val cmd = body.optString("cmd")) {
                "ytmusic" -> parseYtMusic(body, cmd, v, ts, skew)
                "waze" -> parseWaze(body, cmd, v, ts, skew)
                else -> ParseResult.Rejected("unknown cmd=$cmd", skew)
            }
        }

        private fun parseYtMusic(body: JSONObject, cmd: String, v: Int, ts: Long, skew: Long): ParseResult {
            val form = body.optString("form")
            if (form != "playlist" && form != "song") return ParseResult.Rejected("unknown form=$form", skew)

            val id = body.optString("id")
            if (id.isBlank()) return ParseResult.Rejected("blank id", skew)
            if (form == "song" && !VIDEO_ID_REGEX.matches(id)) {
                return ParseResult.Rejected("invalid id for form=song", skew)
            }
            return ParseResult.Accepted(Message(v = v, ts = ts, cmd = cmd, form = form, id = id), skew)
        }

        private fun parseWaze(body: JSONObject, cmd: String, v: Int, ts: Long, skew: Long): ParseResult {
            val url = body.optString("url")
            if (url.isBlank()) return ParseResult.Rejected("missing url", skew)
            if (WAZE_ALLOWED_PREFIXES.none { url.startsWith(it) }) {
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
