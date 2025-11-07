/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import sk.kra.files.provider.kra.client.FileInfo
import sk.kra.files.provider.kra.client.KraApiClient
import sk.kra.files.provider.kra.client.KraAuthority
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Fileinfo cache
 * This significantly reduces the number of fileinfo calls
 */
internal object KraFileInfoCache {

    private data class CacheKey(
        val authority: KraAuthority,
        val ident: String
    )

    private val cache = ConcurrentHashMap<CacheKey, FileInfo>()

    /**
     * Get the attributes for a ident, fetching from API if not cached
     */
    @Throws(IOException::class)
    fun getInfo(
        ident: String,
	authority: KraAuthority,
	client: KraApiClient
    ):FileInfo {
        val cacheKey = CacheKey(authority, ident)

        // Check cache first
        cache[cacheKey]?.let { return it }

	val fileInfo = client.getFileInfo(ident)
	cache[cacheKey] = fileInfo

	return fileInfo
    }

    /**
     * Cache fileinfo for an ident
     */
    fun putInfo(ident: String, authority: KraAuthority, fileinfo: FileInfo) {
        if (ident.isEmpty()) {
            return // Don't cache empty ident
        }

        val cacheKey = CacheKey(authority, ident)
        cache[cacheKey] = fileinfo
    }

    /**
     * Remove attributes from cache (used when file is deleted)
     */
    fun removeInfo(ident: String, authority: KraAuthority) {
        val cacheKey = CacheKey(authority, ident)
        cache.remove(cacheKey)
    }

    /**
     * Clear all cache entries for an authority
     */
    fun clearAuthority(authority: KraAuthority) {
        cache.keys.removeIf { it.authority == authority }
    }

    /**
     * Clear entire cache
     */
    fun clearAll() {
        cache.clear()
    }
}
