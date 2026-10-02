package com.mobplayer.ytcrawler.internal

import com.mobplayer.ytcrawler.exception.RegexMismatchException
import java.util.regex.Matcher
import java.util.regex.Pattern

object RegexUtils {
    @JvmStatic
    fun search(regex: String, source: String): Matcher? {
        val matcher = Pattern.compile(regex).matcher(source)
        return if (matcher.find()) matcher else null
    }

    @JvmStatic
    fun search(regex: String, source: String, fatalMessage: String): Matcher {
        val matcher = Pattern.compile(regex).matcher(source)
        if (!matcher.find()) {
            throw RegexMismatchException(fatalMessage)
        }
        return matcher
    }

    @JvmStatic
    fun sub(regex: String, replacement: String, source: String): String {
        return Pattern.compile(regex).matcher(source).replaceAll(replacement)
    }

    @JvmStatic
    fun sub(regex: String, source: String, replaceEvaluation: (Matcher) -> String): String {
        val matcher = Pattern.compile(regex).matcher(source)
        val sb = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(sb, replaceEvaluation(matcher))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }
}
