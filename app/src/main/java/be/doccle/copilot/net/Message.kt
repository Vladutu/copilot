package be.doccle.copilot.net

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
         * Parses one ntfy event line. Returns the inner Message if it is a fresh,
         * schema-valid command we understand. Returns null for keepalives, malformed
         * input, stale messages, or unknown cmd/form.
         */
        fun parseEnvelope(line: String, nowSec: Long, maxAgeSec: Long): Message? {
            val envelope = try { JSONObject(line) } catch (e: JSONException) { return null }
            if (envelope.optString("event") != "message") return null
            val payload = envelope.optString("message", "")
            if (payload.isEmpty()) return null

            val body = try { JSONObject(payload) } catch (e: JSONException) { return null }

            val v = body.optInt("v", -1)
            if (v != 1) return null

            val ts = body.optLong("ts", -1)
            if (ts < 0) return null
            if (abs(nowSec - ts) > maxAgeSec) return null

            val cmd = body.optString("cmd")
            if (cmd != "ytmusic") return null

            val form = body.optString("form")
            if (form != "playlist") return null

            val id = body.optString("id")
            if (id.isBlank()) return null

            return Message(v = v, ts = ts, cmd = cmd, form = form, id = id)
        }
    }
}
