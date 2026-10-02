package com.mobplayer.ytcrawler

import com.mobplayer.ytcrawler.model.youtube.format.*
import org.junit.Assert.*
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFindNonDashFormat() {
        val format18 = FormatUtils.findByItag("18")
        assertTrue("Format 18 should be NonDash", format18 is NonDash)
        val nonDash = format18 as NonDash
        assertEquals(360, nonDash.height)
        assertEquals(640, nonDash.width)
        assertEquals(Container.MP4, nonDash.container)

        val format22 = FormatUtils.findByItag("22")
        assertTrue("Format 22 should be NonDash 720p", format22 is NonDash)
        assertEquals(720, (format22 as NonDash).height)
    }

    @Test
    fun testFindDashAudioOnlyFormat() {
        val format140 = FormatUtils.findByItag("140")
        assertTrue("Format 140 should be DashAudioOnly", format140 is DashAudioOnly)
        val audio = format140 as DashAudioOnly
        assertEquals(128, audio.audioBitrate)
        assertEquals(AudioEncoding.AAC, audio.audioEncoding)
    }

    @Test
    fun testFindDashVideoOnlyFormat() {
        val format137 = FormatUtils.findByItag("137")
        assertTrue("Format 137 should be DashVideoOnly 1080p", format137 is DashVideoOnly)
        assertEquals(1080, (format137 as DashVideoOnly).height)
        assertEquals(Container.MP4, format137.container)
    }

    @Test
    fun testUnknownFormat() {
        val format9999 = FormatUtils.findByItag("9999")
        assertTrue("Unrecognized itag should return UnknownFormat", format9999 is UnknownFormat)
        assertEquals("9999", format9999.itag)
    }
}
