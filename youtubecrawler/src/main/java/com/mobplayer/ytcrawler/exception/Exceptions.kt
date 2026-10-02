package com.mobplayer.ytcrawler.exception

open class ExtractorException(
    message: String? = null,
    cause: Throwable? = null,
    val vid: String? = null
) : Exception(message, cause)

class AgeRestrictionException(message: String, vid: String) : ExtractorException(message, null, vid)

class BadExtractorException : ExtractorException {
    constructor(message: String, vid: String) : super(message, null, vid)
    constructor(message: String, cause: Throwable, vid: String) : super(message, cause, vid)
}

class HttpClientException(val code: Int, message: String) : Exception("HTTP $code: $message")

class NotSupportedDashDynamicException(message: String, vid: String) : ExtractorException(message, null, vid)

class NotSupportedVideoException(message: String, vid: String) : ExtractorException(message, null, vid)

class RegexMismatchException(message: String) : Exception(message)

class SignatureDecryptException(message: String, cause: Throwable? = null, vid: String? = null) :
    ExtractorException(message, cause, vid)

class VideoNotAvailableException(message: String, vid: String) : ExtractorException(message, null, vid)
