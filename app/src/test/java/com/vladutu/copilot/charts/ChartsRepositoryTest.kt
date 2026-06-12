package com.vladutu.copilot.charts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeFetcher : ChartFetcher {
    var byUrl: Map<String, List<String>> = emptyMap()
    var failUrls: Set<String> = emptySet()

    override suspend fun fetchVideoIds(playlistUrl: String): List<String> {
        if (playlistUrl in failUrls) throw ChartsException("boom")
        return byUrl[playlistUrl] ?: emptyList()
    }
}

private class FakeMinter : PlaylistMinter {
    var mintedIds: List<String>? = null
    var throwOnMint = false

    override suspend fun mint(videoIds: List<String>): String {
        if (throwOnMint) throw ChartsException("mint boom")
        mintedIds = videoIds
        return "https://music.youtube.com/watch?v=${videoIds.first()}&list=TLGGfake"
    }
}

class ChartsRepositoryTest {

    private val fetcher = FakeFetcher()
    private val minter = FakeMinter()
    private val repo = ChartsRepository(fetcher, minter)

    @Test fun `mints the US-priority interleaved dedupe of both charts`() = runTest {
        fetcher.byUrl = mapOf(
            ChartsRepository.US_CHART_URL to listOf("us1", "shared", "us3"),
            ChartsRepository.GB_CHART_URL to listOf("shared", "gb2", "gb3"),
        )
        val url = repo.topWeeklyLaunchUrl()
        assertEquals("https://music.youtube.com/watch?v=us1&list=TLGGfake", url)
        assertEquals(listOf("us1", "shared", "gb2", "us3", "gb3"), minter.mintedIds)
    }

    @Test fun `one failed chart degrades to the other alone`() = runTest {
        fetcher.byUrl = mapOf(ChartsRepository.US_CHART_URL to listOf("us1", "us2"))
        fetcher.failUrls = setOf(ChartsRepository.GB_CHART_URL)
        repo.topWeeklyLaunchUrl()
        assertEquals(listOf("us1", "us2"), minter.mintedIds)
    }

    @Test fun `both charts failing falls back to the US chart playlist`() = runTest {
        fetcher.failUrls = setOf(ChartsRepository.US_CHART_URL, ChartsRepository.GB_CHART_URL)
        assertEquals(ChartsRepository.FALLBACK_URL, repo.topWeeklyLaunchUrl())
        assertNull(minter.mintedIds)
    }

    @Test fun `mint failure falls back to the US chart playlist`() = runTest {
        fetcher.byUrl = mapOf(ChartsRepository.US_CHART_URL to listOf("us1"))
        minter.throwOnMint = true
        assertEquals(ChartsRepository.FALLBACK_URL, repo.topWeeklyLaunchUrl())
    }
}
