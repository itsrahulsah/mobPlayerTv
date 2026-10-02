package com.mobplayer.ytcrawler.internal

import com.mobplayer.ytcrawler.Const
import com.mobplayer.ytcrawler.exception.RegexMismatchException
import com.mobplayer.ytcrawler.model.youtube.WindowSettings
import com.google.gson.Gson
import okhttp3.Request
import java.io.Closeable
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.regex.Pattern

object Utils {
    private val XS_DURATION_PATTERN: Pattern =
        Pattern.compile("^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$")

    @JvmStatic
    fun addXYouTubeHeader(builder: Request.Builder, windowSettings: WindowSettings) {
        windowSettings.variantChecksum?.let { builder.header(Headers.X_YOUTUBE_VARIANTS_CHECKSUM, it) }
        windowSettings.buildLabel?.let { builder.header(Headers.X_YOUTUBE_PAGE_LABEL, it) }
        windowSettings.buildId?.let { builder.header(Headers.X_YOUTUBE_PAGE_CL, it) }
        windowSettings.clientVersion?.let { builder.header(Headers.X_YOUTUBE_CLIENT_VERSION, it) }
        windowSettings.clientName?.let { builder.header(Headers.X_YOUTUBE_CLIENT_NAME, it) }
    }

    @JvmStatic
    fun mobileWebPageDownloadRequestBuilder(url: String): Request.Builder {
        return Request.Builder().url(url)
            .addHeader(Headers.HTTP_ACCEPT, C.BROWSER_ACCEPT)
            .addHeader(Headers.HTTP_ACCEPT_LANGUAGE, C.BROWSER_ACCEPT_LANGUAGE)
            .addHeader(Headers.HTTP_ACCEPT_CHARSET, C.BROWSER_ACCEPT_CHARSET)
            .addHeader(Headers.HTTP_USER_AGENT, C.MOBILE_BROWSER_USER_AGENT)
    }

    @JvmStatic
    fun desktopWebPageDownloadRequestBuilder(url: String): Request.Builder {
        return Request.Builder().url(url)
            .addHeader(Headers.HTTP_ACCEPT, C.BROWSER_ACCEPT)
            .addHeader(Headers.HTTP_ACCEPT_LANGUAGE, C.BROWSER_ACCEPT_LANGUAGE)
            .addHeader(Headers.HTTP_ACCEPT_CHARSET, C.BROWSER_ACCEPT_CHARSET)
            .addHeader(Headers.HTTP_USER_AGENT, C.DESKTOP_BROWSER_USER_AGENT)
    }

    @JvmStatic
    fun parseWindowSettings(gson: Gson, webPage: String): WindowSettings {
        val matcher = RegexUtils.search("window\\.settings\\s*=\\s*(\\{.+?\\})\\s*;", webPage)
        if (matcher != null) {
            val json = matcher.group(1)
            return gson.fromJson(json, WindowSettings::class.java)
        }
        throw RegexMismatchException("Couldn't parse windows settings")
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    }

    @JvmStatic
    fun unescapeUtf32(source: String): String {
        return RegexUtils.sub("\\\\U[0-9a-fA-F]{8}", source) { matcher ->
            val hex = matcher.group(0).replace("\\U", "0x")
            try {
                java.lang.String(intToByteArray(Integer.decode(hex)), "utf-32").toString()
            } catch (e: UnsupportedEncodingException) {
                ""
            }
        }
    }

    @JvmStatic
    fun <T> parseAjaxResponse(gson: Gson, ajaxRes: String, tClass: Class<T>): T {
        val offset = ajaxRes.indexOf("{")
        val clean = if (offset >= 0) unescapeUtf32(ajaxRes.substring(offset)) else unescapeUtf32(ajaxRes)
        return gson.fromJson(clean, tClass)
    }

    @JvmStatic
    fun createAjaxRequest(url: String, referer: String, windowSettings: WindowSettings): Request {
        val fullUrl = getYouTubeFullUrl(url)
        val requestBuilder = Request.Builder().url(fullUrl)
            .addHeader(Headers.HTTP_ACCEPT, C.BROWSER_ACCEPT)
            .addHeader(Headers.HTTP_REFERER, referer)
            .addHeader(Headers.HTTP_ACCEPT_CHARSET, C.BROWSER_ACCEPT_CHARSET)
            .addHeader(Headers.HTTP_ACCEPT_LANGUAGE, C.BROWSER_ACCEPT_LANGUAGE)
            .addHeader(Headers.HTTP_USER_AGENT, C.MOBILE_BROWSER_USER_AGENT)
        addXYouTubeHeader(requestBuilder, windowSettings)
        return requestBuilder.build()
    }

    @JvmStatic
    fun getYouTubeFullUrl(endpoint: String): String {
        return when {
            endpoint.startsWith("//") -> "https:$endpoint"
            endpoint.startsWith("/") -> "https://m.youtube.com$endpoint"
            else -> endpoint
        }
    }

    @JvmStatic
    fun splitQuery(query: String): Map<String, List<String>> {
        val queryPairs = LinkedHashMap<String, MutableList<String>>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            val key = if (idx > 0) URLDecoder.decode(pair.substring(0, idx), "UTF-8") else pair
            if (!queryPairs.containsKey(key)) {
                queryPairs[key] = mutableListOf()
            }
            val value = if (idx > 0 && pair.length > idx + 1) {
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            } else null
            if (value != null) {
                queryPairs[key]?.add(value)
            }
        }
        return queryPairs
    }

    @JvmStatic
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun isEmpty(s: CharSequence?): Boolean = s.isNullOrEmpty()

    @JvmStatic
    fun <T> isEmpty(collection: Collection<T>?): Boolean = collection.isNullOrEmpty()

    @JvmStatic
    fun simpleXmlUnescape(text: String): String {
        val result = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            val charAt = text[i]
            if (charAt != '&') {
                result.append(charAt)
                i++
            } else {
                when {
                    text.startsWith("&amp;", i) -> {
                        result.append('&')
                        i += 5
                    }
                    text.startsWith("&apos;", i) -> {
                        result.append('\'')
                        i += 6
                    }
                    text.startsWith("&quot;", i) -> {
                        result.append('"')
                        i += 6
                    }
                    text.startsWith("&lt;", i) -> {
                        result.append('<')
                        i += 4
                    }
                    text.startsWith("&gt;", i) -> {
                        result.append('>')
                        i += 4
                    }
                    else -> i++
                }
            }
        }
        return result.toString()
    }

    @JvmStatic
    fun safeParse(number: String?, fallback: Int): Int {
        if (number.isNullOrEmpty()) return fallback
        return try {
            number.toInt()
        } catch (_: Throwable) {
            fallback
        }
    }

    @JvmStatic
    fun safeParse(number: String?, fallback: Float): Float {
        if (number.isNullOrEmpty()) return fallback
        return try {
            number.toFloat()
        } catch (_: Throwable) {
            fallback
        }
    }

    @JvmStatic
    fun parseXsDuration(value: String?): Long {
        if (value.isNullOrEmpty()) return Const.UNKNOWN_VALUE.toLong()
        val matcher = XS_DURATION_PATTERN.matcher(value)
        return if (matcher.matches()) {
            val negated = !matcher.group(1).isNullOrEmpty()
            val years = matcher.group(3)
            var durationSeconds = if (years != null) years.toDouble() * 31556908.0 else 0.0
            val months = matcher.group(5)
            durationSeconds += if (months != null) months.toDouble() * 2629739.0 else 0.0
            val days = matcher.group(7)
            durationSeconds += if (days != null) days.toDouble() * 86400.0 else 0.0
            val hours = matcher.group(10)
            durationSeconds += if (hours != null) hours.toDouble() * 3600.0 else 0.0
            val minutes = matcher.group(12)
            durationSeconds += if (minutes != null) minutes.toDouble() * 60.0 else 0.0
            val seconds = matcher.group(14)
            durationSeconds += if (seconds != null) seconds.toDouble() else 0.0
            val durationMillis = (durationSeconds * 1000.0).toLong()
            if (negated) -durationMillis else durationMillis
        } else {
            try {
                (value.toDouble() * 3600.0 * 1000.0).toLong()
            } catch (_: Throwable) {
                Const.UNKNOWN_VALUE.toLong()
            }
        }
    }
}
