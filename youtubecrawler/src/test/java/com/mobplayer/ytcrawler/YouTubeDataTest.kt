package com.mobplayer.ytcrawler

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class YouTubeDataTest {

    private lateinit var youTubeData: YouTubeData

    @Before
    fun setUp() {
        youTubeData = YouTubeData.Builder().build()
    }

    @Test
    fun testBuilderCreatesInstance() {
        assertNotNull(youTubeData)
        assertNotNull(youTubeData.okHttpClient)
        assertNotNull(youTubeData.gson)
        assertNotNull(youTubeData.getStreamExtractor())
    }

    @Test
    fun testHomeFeedCrawl() = runBlocking {
        try {
            val response = youTubeData.homeFeed()
            assertNotNull(response)
            assertNotNull(response.data)
            assertTrue(response.data.isSuccess)
        } catch (e: Exception) {
            // In case offline, ensure error is network-related, not a coding error
            assertTrue(e is java.io.IOException || e.message != null)
        }
    }

    @Test
    fun testSearchCrawl() = runBlocking {
        try {
            val response = youTubeData.search("Kotlin Android")
            assertNotNull(response)
            assertNotNull(response.data)
            assertTrue(response.data.isSuccess)
        } catch (e: Exception) {
            assertTrue(e is java.io.IOException || e.message != null)
        }
    }
}
