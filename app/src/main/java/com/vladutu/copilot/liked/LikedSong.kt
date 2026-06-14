package com.vladutu.copilot.liked

import kotlinx.serialization.Serializable

/**
 * One entry in the local Liked list. Text-only by design: there is no reliable way
 * to get the YouTube videoId of the externally-playing track, so we store what the
 * media session exposes — title + (optional) artist — plus when it was saved.
 */
@Serializable
data class LikedSong(
    val title: String,
    val artist: String? = null,
    val savedAt: Long,
) {
    /** Identity for dedup: title + artist compared trimmed and case-insensitively;
     *  null and blank artist are treated as the same "no artist". */
    fun sameSongAs(other: LikedSong): Boolean =
        title.trim().equals(other.title.trim(), ignoreCase = true) &&
            normArtist(artist) == normArtist(other.artist)

    private fun normArtist(a: String?): String = a?.trim()?.lowercase() ?: ""
}
