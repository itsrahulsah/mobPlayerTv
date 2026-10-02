package com.mobplayer.ytcrawler.internal

object UriUtil {
    private const val INDEX_COUNT = 4
    private const val SCHEME_COLON = 0
    private const val PATH = 1
    private const val QUERY = 2
    private const val FRAGMENT = 3

    @JvmStatic
    fun resolve(baseUri: String?, referenceUri: String?): String {
        val base = baseUri ?: ""
        val ref = referenceUri ?: ""
        val uri = StringBuilder()

        val refIndices = getUriIndices(ref)
        if (refIndices[SCHEME_COLON] != -1) {
            uri.append(ref)
            removeDotSegments(uri, refIndices[PATH], refIndices[QUERY])
            return uri.toString()
        }

        val baseIndices = getUriIndices(base)
        if (refIndices[FRAGMENT] == 0) {
            return uri.append(base, 0, baseIndices[FRAGMENT]).append(ref).toString()
        }

        if (refIndices[QUERY] == 0) {
            return uri.append(base, 0, baseIndices[QUERY]).append(ref).toString()
        }

        if (refIndices[PATH] != 0) {
            val baseLimit = baseIndices[SCHEME_COLON] + 1
            uri.append(base, 0, baseLimit).append(ref)
            return removeDotSegments(uri, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY])
        }

        if (ref.isNotEmpty() && ref[refIndices[PATH]] == '/') {
            uri.append(base, 0, baseIndices[PATH]).append(ref)
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY])
        }

        if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH] && baseIndices[PATH] == baseIndices[QUERY]) {
            uri.append(base, 0, baseIndices[PATH]).append('/').append(ref)
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1)
        } else {
            val lastSlashIndex = base.lastIndexOf('/', baseIndices[QUERY] - 1)
            val baseLimit = if (lastSlashIndex == -1) baseIndices[PATH] else lastSlashIndex + 1
            uri.append(base, 0, baseLimit).append(ref)
            return removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY])
        }
    }

    private fun removeDotSegments(uri: StringBuilder, offset: Int, limit: Int): String {
        var mutableOffset = offset
        var mutableLimit = limit
        if (mutableOffset >= mutableLimit) return uri.toString()
        if (uri[mutableOffset] == '/') mutableOffset++

        var segmentStart = mutableOffset
        var i = mutableOffset
        while (i <= mutableLimit) {
            val nextSegmentStart: Int = when {
                i == mutableLimit -> i
                uri[i] == '/' -> i + 1
                else -> {
                    i++
                    continue
                }
            }
            if (i == segmentStart + 1 && uri[segmentStart] == '.') {
                uri.delete(segmentStart, nextSegmentStart)
                mutableLimit -= nextSegmentStart - segmentStart
                i = segmentStart
            } else if (i == segmentStart + 2 && uri[segmentStart] == '.' && uri[segmentStart + 1] == '.') {
                val prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1
                val removeFrom = if (prevSegmentStart > mutableOffset) prevSegmentStart else mutableOffset
                uri.delete(removeFrom, nextSegmentStart)
                mutableLimit -= nextSegmentStart - removeFrom
                segmentStart = prevSegmentStart
                i = prevSegmentStart
            } else {
                i++
                segmentStart = i
            }
        }
        return uri.toString()
    }

    private fun getUriIndices(uriString: String): IntArray {
        val indices = IntArray(INDEX_COUNT)
        if (uriString.isEmpty()) {
            indices[SCHEME_COLON] = -1
            return indices
        }
        val length = uriString.length
        var fragmentIndex = uriString.indexOf('#')
        if (fragmentIndex == -1) fragmentIndex = length
        var queryIndex = uriString.indexOf('?')
        if (queryIndex == -1 || queryIndex > fragmentIndex) queryIndex = fragmentIndex

        val schemeIndexLimit = uriString.indexOf('/').let {
            if (it == -1 || it > queryIndex) queryIndex else it
        }
        var schemeIndex = uriString.indexOf(':')
        if (schemeIndex > schemeIndexLimit) schemeIndex = -1

        val hasAuthority = schemeIndex + 2 < queryIndex &&
                uriString.getOrNull(schemeIndex + 1) == '/' &&
                uriString.getOrNull(schemeIndex + 2) == '/'

        val pathIndex = if (hasAuthority) {
            val idx = uriString.indexOf('/', schemeIndex + 3)
            if (idx == -1 || idx > queryIndex) queryIndex else idx
        } else {
            schemeIndex + 1
        }

        indices[SCHEME_COLON] = schemeIndex
        indices[PATH] = pathIndex
        indices[QUERY] = queryIndex
        indices[FRAGMENT] = fragmentIndex
        return indices
    }
}
