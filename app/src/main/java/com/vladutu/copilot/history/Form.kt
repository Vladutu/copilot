package com.vladutu.copilot.history

import kotlinx.serialization.Serializable

@Serializable
enum class Form {
    PLAYLIST,
    SONG,
    DESTINATION,
    RADIO;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): Form? = when (value) {
            "playlist" -> PLAYLIST
            "song" -> SONG
            "destination" -> DESTINATION
            "radio" -> RADIO
            else -> null
        }
    }
}
