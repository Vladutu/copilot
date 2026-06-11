package com.vladutu.copilot.discover

/** Backend-independent YT Music URL builders (kept out of MusicSearcher on purpose). */
object YtMusicUrls {
    // watch?list= starts playback immediately; playlist?list= would open the
    // playlist's browse page instead. Same shuffled form Pilot publishes.
    fun playlist(playlistId: String): String =
        "https://music.youtube.com/watch?list=$playlistId&shuffle=1"

    /** YT Music "song radio": endless generated mix seeded from one video. */
    fun radioMix(videoId: String): String =
        "https://music.youtube.com/watch?v=$videoId&list=RDAMVM$videoId"
}
