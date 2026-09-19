package com.mobplayer.tv.data

import com.mobplayer.tv.data.models.CardType
import org.junit.Assert.*
import org.junit.Test

class MockMediaRepositoryTest {

    @Test
    fun testFeaturedItem_isValid() {
        val featured = MockMediaRepository.featuredItem
        assertNotNull(featured)
        assertTrue(featured.title.isNotEmpty())
        assertTrue(featured.videoUrl.startsWith("http"))
        assertTrue(featured.genres.isNotEmpty())
    }

    @Test
    fun testContentRails_containAllExpectedCategories() {
        val rails = MockMediaRepository.contentRails
        assertEquals(4, rails.size)

        val railTypes = rails.map { it.cardType }
        assertTrue(railTypes.contains(CardType.CONTINUE_WATCHING))
        assertTrue(railTypes.contains(CardType.POSTER))
        assertTrue(railTypes.contains(CardType.LANDSCAPE))
        assertTrue(railTypes.contains(CardType.LIVE))
    }

    @Test
    fun testTopMoviesRail_hasNumberedRankings() {
        val moviesRail = MockMediaRepository.contentRails.first { it.cardType == CardType.POSTER }
        assertTrue(moviesRail.items.isNotEmpty())
        
        moviesRail.items.forEachIndexed { index, item ->
            assertEquals(index + 1, item.rankNumber)
            assertTrue(item.videoUrl.isNotEmpty())
        }
    }

    @Test
    fun testContinueWatchingRail_hasProgressData() {
        val continueWatchingRail = MockMediaRepository.contentRails.first { it.cardType == CardType.CONTINUE_WATCHING }
        assertTrue(continueWatchingRail.items.isNotEmpty())

        continueWatchingRail.items.forEach { item ->
            assertNotNull(item.progress)
            assertTrue(item.progress!! in 0.0f..1.0f)
            assertNotNull(item.remainingTime)
        }
    }

    @Test
    fun testLiveChannelsRail_markedAsLive() {
        val liveRail = MockMediaRepository.contentRails.first { it.cardType == CardType.LIVE }
        assertTrue(liveRail.items.isNotEmpty())

        liveRail.items.forEach { item ->
            assertTrue(item.isLive)
            assertNotNull(item.channelNumber)
        }
    }
}
