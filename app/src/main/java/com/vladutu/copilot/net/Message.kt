package com.vladutu.copilot.net

import com.vladutu.copilot.history.Form
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

data class Message(
    val v: Int,
    val ts: Long,
    val cmd: String,
    val form: Form,
    val url: String,
    val title: String?,
    val imageUrl: String?,
) {
    companion object {
        private const val SCHEMA_VERSION = 3

        private val YT_MUSIC_ALLOWED_PREFIXES = listOf(
            "https://music.youtube.com/",
        )
        private val WAZE_ALLOWED_PREFIXES = listOf(
            "https://ul.waze.com/",
            "https://waze.com/",
            "https://www.waze.com/",
        )
        private val MAPS_ALLOWED_PREFIXES = listOf(
            "https://www.google.com/maps",
            "https://maps.google.com/",
            "https://maps.app.goo.gl/",
            "https://goo.gl/maps/",
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
            if (abs(skew) > maxAgeSec) return ParseResult.Rejected("stale (${skew}s)", skew)

            val cmd = body.optString("cmd")
            val allowedPrefixes = when (cmd) {
                "ytmusic" -> YT_MUSIC_ALLOWED_PREFIXES
                "waze" -> WAZE_ALLOWED_PREFIXES
                "maps" -> MAPS_ALLOWED_PREFIXES
                else -> return ParseResult.Rejected("unknown cmd=$cmd", skew)
            }

            val form = Form.fromWire(body.optString("form").takeIf { it.isNotBlank() })
                ?: return ParseResult.Rejected("unknown form", skew)

            val cmdFormConsistent = when (cmd) {
                "ytmusic" -> form == Form.PLAYLIST || form == Form.SONG
                "waze", "maps" -> form == Form.DESTINATION
                else -> false
            }
            if (!cmdFormConsistent) return ParseResult.Rejected("cmd/form mismatch", skew)

            val url = body.optString("url")
            if (url.isBlank()) return ParseResult.Rejected("missing url", skew)
            if (allowedPrefixes.none { url.startsWith(it) }) {
                return ParseResult.Rejected("untrusted host", skew)
            }

            val title = body.optString("title").takeIf { it.isNotBlank() }
            val imageUrl = body.optString("imageUrl").takeIf { it.isNotBlank() }

            return ParseResult.Accepted(
                Message(v = v, ts = ts, cmd = cmd, form = form, url = url, title = title, imageUrl = imageUrl),
                skew,
            )
        }
    }
}

sealed class ParseResult {
    object Skipped : ParseResult()
    data class Rejected(val reason: String, val skewSec: Long? = null) : ParseResult()
    data class Accepted(val message: Message, val skewSec: Long) : ParseResult()
}

/**
 * Whether this message should be persisted to Copilot's history list after a
 * successful launch. The "maps" cmd is a one-off launch override for a
 * destination that already exists in history (as a Waze entry, sha1-keyed on
 * the Waze URL); saving a second row keyed on the Maps URL would look like a
 * duplicate entry for the same physical place.
 */
fun Message.savesToHistory(): Boolean = cmd != "maps"
