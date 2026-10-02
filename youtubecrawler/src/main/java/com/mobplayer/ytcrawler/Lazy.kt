package com.mobplayer.ytcrawler

open class Lazy<T>(private val supplier: () -> T) {
    @Volatile
    private var value: T? = null

    open fun get(): T {
        val current = value
        if (current != null) return current
        return synchronized(this) {
            val doubleCheck = value
            if (doubleCheck != null) {
                doubleCheck
            } else {
                val computed = supplier()
                value = computed
                computed
            }
        }
    }
}
