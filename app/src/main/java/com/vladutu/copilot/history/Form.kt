package com.vladutu.copilot.history

import kotlinx.serialization.Serializable

@Serializable
enum class Form {
    PLAYLIST,
    SONG,
    DESTINATION;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): Form? = when (value) {
            "playlist" -> PLAYLIST
            "song" -> SONG
            "destination" -> DESTINATION
            else -> null
        }
    }
}
