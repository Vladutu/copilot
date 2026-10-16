package com.vladutu.copilot.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeRouteTest {

    @Test fun `single song becomes its Mix as a playlist url`() {
        // NewPipe treats any url with v= as a single video, so the Mix must be a playlist
        // url with no v; NewPipe derives the seed song from the RD id.
        assertEquals(
            "https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ",
            NewPipeRoute.rewrite("https://music.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test fun `song that already carries a list keeps it`() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc&list=PLxyz",
            NewPipeRoute.rewrite("https://music.youtube.com/watch?v=abc&list=PLxyz"),
        )
    }

    @Test fun `playlist url only changes host`() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLxyz",
            NewPipeRoute.rewrite("https://music.youtube.com/playlist?list=PLxyz"),
        )
    }

    @Test fun `song with extra params drops them in favour of the Mix`() {
        assertEquals(
            "https://www.youtube.com/playlist?list=RDabc",
            NewPipeRoute.rewrite("https://music.youtube.com/watch?v=abc&feature=share"),
        )
    }

    @Test fun `www youtube com song is also given a Mix`() {
        assertEquals(
            "https://www.youtube.com/playlist?list=RDabc",
            NewPipeRoute.rewrite("https://www.youtube.com/watch?v=abc"),
        )
    }

    @Test fun `non youtube url is left alone`() {
        assertEquals(
            "https://soundcloud.com/a/b",
            NewPipeRoute.rewrite("https://soundcloud.com/a/b"),
        )
    }

    @Test fun `wantsShuffle is true only for the shuffle marker`() {
        assertTrue(NewPipeRoute.wantsShuffle("https://music.youtube.com/watch?list=PLx&shuffle=1"))
        assertFalse(NewPipeRoute.wantsShuffle("https://music.youtube.com/watch?list=PLx"))
        assertFalse(NewPipeRoute.wantsShuffle("https://music.youtube.com/watch?v=abc"))
        assertFalse(NewPipeRoute.wantsShuffle("https://music.youtube.com/watch?list=PLx&shuffle=0"))
    }
}
