package com.vladutu.copilot.discover

/** Backend-independent YT Music URL builders (kept out of MusicSearcher on purpose). */
object YtMusicUrls {
    fun playlist(playlistId: String): String =
        "https://music.youtube.com/playlist?list=$playlistId"

    /** YT Music "song radio": endless generated mix seeded from one video. */
    fun radioMix(videoId: String): String =
        "https://music.youtube.com/watch?v=$videoId&list=RDAMVM$videoId"
}
