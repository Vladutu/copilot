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
    fun sameSongAs(other: LikedSong): Boolean = matches(other.title, other.artist)

    /** Same identity rule as [sameSongAs], against a raw title/artist pair — lets the UI
     *  check whether a NowPlaying track is already in the list without building a LikedSong. */
    fun matches(title: String, artist: String?): Boolean =
        this.title.trim().equals(title.trim(), ignoreCase = true) &&
            normArtist(this.artist) == normArtist(artist)

    private fun normArtist(a: String?): String = a?.trim()?.lowercase() ?: ""
}
