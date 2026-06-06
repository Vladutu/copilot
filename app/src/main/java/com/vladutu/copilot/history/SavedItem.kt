package com.vladutu.copilot.history

import com.vladutu.copilot.launch.PlaylistIdParser
import com.vladutu.copilot.net.Message
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class SavedItem(
    val form: Form,
    val id: String,
    val title: String?,
    val imageUrl: String?,
    val url: String,
    val savedAt: Long,
) {
    companion object
}

fun SavedItem.Companion.from(message: Message, savedAt: Long): SavedItem {
    val id = when (message.form) {
        Form.PLAYLIST -> PlaylistIdParser.parse(message.url) ?: sha1(message.url)
        Form.SONG -> Regex("""[?&]v=([A-Za-z0-9_\-]+)""").find(message.url)?.groupValues?.get(1)
            ?: sha1(message.url)
        Form.DESTINATION -> sha1(message.url)
        Form.RADIO -> sha1(message.url)
    }
    return SavedItem(
        form = message.form,
        id = id,
        title = message.title,
        imageUrl = message.imageUrl,
        url = message.url,
        savedAt = savedAt,
    )
}

private fun sha1(s: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
