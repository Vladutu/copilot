package com.vladutu.copilot.launch

/**
 * Routes `ytmusic` launches to NewPipe instead of the YouTube Music app, and `youtube`
 * launches to NewPipe instead of the YouTube app (those URLs are passed through as-is).
 *
 * Why: the YouTube Music app refuses background playback without Premium, and Copilot's
 * auto-return pushes it to the background right after every command. NewPipe plays the
 * audio-only stream in the background with no Google account at all.
 *
 * Pure Kotlin so the URL rules are unit-testable. [enabled] mirrors the Settings toggle
 * (kept current by [com.vladutu.copilot.CopilotApp], same pattern as
 * [com.vladutu.copilot.split.SplitScreen.enabled]); [com.vladutu.copilot.launch.AppLauncher]
 * reads it synchronously at launch time.
 */
object NewPipeRoute {

    const val PKG = "org.schabi.newpipe"

    /** Defaults off: launches keep going to YouTube Music until the driver opts in. */
    @Volatile var enabled: Boolean = false

    private val YOUTUBE_HOSTS = setOf("music.youtube.com", "www.youtube.com", "youtube.com", "m.youtube.com")
    private val HOST = Regex("^(https?://)([^/?#]+)")
    private val VIDEO_PARAM = Regex("[?&]v=([A-Za-z0-9_-]+)")
    private val LIST_PARAM = Regex("[?&]list=")
    private val SHUFFLE_PARAM = Regex("[?&]shuffle=1(&|$)")

    /**
     * Rewrite a YouTube Music URL for NewPipe:
     *  - host becomes `www.youtube.com` (NewPipe's canonical host; playlists, minted
     *    Top Weekly / Time Machine lists and Discover results all pass through here);
     *  - a single song (has `v`, no `list`) becomes its Mix — an endless on-genre radio
     *    seeded by the song. NewPipe classifies any URL carrying `v=` as a lone video, so
     *    the Mix has to be a *playlist* URL with the `RD<id>` list and no `v`; NewPipe
     *    derives the seed song from the id.
     * Non-YouTube URLs are returned unchanged.
     */
    fun rewrite(url: String): String {
        val match = HOST.find(url) ?: return url
        val host = match.groupValues[2].lowercase()
        if (host !in YOUTUBE_HOSTS) return url
        val scheme = match.groupValues[1]
        val rest = url.substring(match.range.last + 1)
        if (!LIST_PARAM.containsMatchIn(rest)) {
            VIDEO_PARAM.find(rest)?.groupValues?.get(1)?.let { id ->
                return scheme + "www.youtube.com/playlist?list=RD" + id
            }
        }
        return scheme + "www.youtube.com" + rest
    }

    /** Pilot (and Discover) mark a shuffled playlist with `shuffle=1` — a YouTube Music app
     *  convention NewPipe ignores, so [ShuffleRequest] applies it over the media session. */
    fun wantsShuffle(url: String): Boolean = SHUFFLE_PARAM.containsMatchIn(url)
}
