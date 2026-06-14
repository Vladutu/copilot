package com.vladutu.copilot.nowplaying

/** Title + optional artist of the track currently playing in YouTube Music. */
data class NowPlaying(
    val title: String,
    val artist: String?,
)
