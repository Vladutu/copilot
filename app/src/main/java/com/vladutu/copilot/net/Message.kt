package com.vladutu.copilot.net

import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

data class Message(
    val v: Int,
    val ts: Long,
    val cmd: String,
    val form: String,
    val id: String,
) {
    companion object {
        /**
         * Parses one ntfy event line. Returns a [ParseResult] describing exactly
         * what happened so callers can surface the reason on a status screen
         * instead of swallowing the failure silently.
         */
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

            val cmd = body.optString("cmd")
            if (cmd != "ytmusic") return ParseResult.Rejected("unknown cmd=$cmd", skew)

            val form = body.optString("form")
            if (form != "playlist") return ParseResult.Rejected("unknown form=$form", skew)

            val id = body.optString("id")
            if (id.isBlank()) return ParseResult.Rejected("blank id", skew)

            return ParseResult.Accepted(Message(v, ts, cmd, form, id), skew)
        }
    }
}

sealed class ParseResult {
    /** Not for us at all — keepalive, malformed envelope. Don't surface. */
    object Skipped : ParseResult()

    /** Parsed but failed validation. `skewSec` set when we got far enough to read `ts`. */
    data class Rejected(val reason: String, val skewSec: Long? = null) : ParseResult()

    /** Valid, fresh, understood. */
    data class Accepted(val message: Message, val skewSec: Long) : ParseResult()
}
