package com.mobplayer.ytcrawler

import com.mobplayer.ytcrawler.internal.UriUtil
import com.mobplayer.ytcrawler.internal.Utils
import org.junit.Assert.*
import org.junit.Test

class UtilsTest {

    @Test
    fun testUriResolution() {
        val base = "https://www.youtube.com/watch?v=12345"
        val relative = "/results?search_query=kotlin"
        val resolved = UriUtil.resolve(base, relative)
        assertEquals("https://www.youtube.com/results?search_query=kotlin", resolved)
    }

    @Test
    fun testParseXsDuration() {
        val duration = "PT3M45S"
        val millis = Utils.parseXsDuration(duration)
        assertEquals((3 * 60 + 45) * 1000L, millis)
    }

    @Test
    fun testSimpleXmlUnescape() {
        val escaped = "Tom &amp; Jerry &quot;Classic&quot; &lt;Cartoons&gt;"
        val unescaped = Utils.simpleXmlUnescape(escaped)
        assertEquals("Tom & Jerry \"Classic\" <Cartoons>", unescaped)
    }

    @Test
    fun testSplitQuery() {
        val query = "v=dQw4w9WgXcQ&list=PL12345&index=3"
        val map = Utils.splitQuery(query)
        assertEquals(listOf("dQw4w9WgXcQ"), map["v"])
        assertEquals(listOf("PL12345"), map["list"])
        assertEquals(listOf("3"), map["index"])
    }
}
