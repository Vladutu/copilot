package com.vladutu.copilot.nowplaying

/** Title + optional artist of the track currently playing in a music app (YT Music or SoundCloud). */
data class NowPlaying(
    val title: String,
    val artist: String?,
)
