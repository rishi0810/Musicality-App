package com.proj.Musicality.cache

class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun remove(key: K) { map.remove(key) }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
        if (map.size > maxSize) {
            val eldest = map.entries.first()
            map.remove(eldest.key)
        }
    }
}
