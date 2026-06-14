package com.vladutu.copilot.ui.home

/** Pure knob-stop arithmetic for the Home screen. Four fixed tiles
 *  (Waze, Maps, Places, Music); a fifth stop — the Like heart — appears only while a
 *  song is playing, and only as the LAST stop, so default focus (index 0) never lands
 *  on it and a left-twist from Waze never reaches it. */
object HomeKnob {
    const val BASE_TILES = 4

    fun tileCount(songPlaying: Boolean): Int = if (songPlaying) BASE_TILES + 1 else BASE_TILES

    fun clampFocus(focused: Int, tileCount: Int): Int = focused.coerceIn(0, tileCount - 1)
}
