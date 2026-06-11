package com.vladutu.copilot.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class YtMusicUrlsTest {
    @Test fun `playlist url`() =
        assertEquals("https://music.youtube.com/playlist?list=PL123", YtMusicUrls.playlist("PL123"))

    @Test fun `radio mix url repeats the seed in the RDAMVM list`() =
        assertEquals("https://music.youtube.com/watch?v=abc&list=RDAMVMabc", YtMusicUrls.radioMix("abc"))
}
