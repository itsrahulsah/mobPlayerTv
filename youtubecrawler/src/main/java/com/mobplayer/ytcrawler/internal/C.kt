package com.mobplayer.ytcrawler.internal

import java.util.TimeZone

object Headers {
    const val HTTP_ACCEPT: String = "Accept"
    const val HTTP_ACCEPT_LANGUAGE: String = "Accept-Language"
    const val HTTP_ACCEPT_CHARSET: String = "Accept-Charset"
    const val HTTP_USER_AGENT: String = "User-Agent"
    const val HTTP_REFERER: String = "Referer"
    const val X_YOUTUBE_CLIENT_NAME: String = "X-YouTube-Client-Name"
    const val X_YOUTUBE_CLIENT_VERSION: String = "X-YouTube-Client-Version"
    const val X_YOUTUBE_PAGE_CL: String = "X-YouTube-Page-CL"
    const val X_YOUTUBE_PAGE_LABEL: String = "X-YouTube-Page-Label"
    const val X_YOUTUBE_VARIANTS_CHECKSUM: String = "X-YouTube-Variants-Checksum"
}

object C {
    val UTC_OFFSET: Int
        get() = TimeZone.getDefault().rawOffset / (60 * 1000)

    const val BROWSER_ACCEPT: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    const val BROWSER_ACCEPT_LANGUAGE: String = "en-us,en;q=0.5"
    const val BROWSER_ACCEPT_CHARSET: String = "ISO-8859-1,utf-8;q=0.7,*;q=0.7"
    const val MOBILE_BROWSER_USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 4.3; Nexus 10 Build/JSS15Q) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36"
    const val DESKTOP_BROWSER_USER_AGENT: String =
        "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)"
}
