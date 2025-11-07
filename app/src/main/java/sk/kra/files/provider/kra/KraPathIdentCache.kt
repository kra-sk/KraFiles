/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import sk.kra.files.provider.kra.client.KraApiClient
import sk.kra.files.provider.kra.client.KraAuthority
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for mapping KRA paths to file idents
 * This is necessary because KRA uses opaque identifiers for files/folders
 */
internal object KraPathIdentCache {

    private data class CacheKey(
        val authority: KraAuthority,
        val path: String
    )

    private val cache = ConcurrentHashMap<CacheKey, String>()

    /**
     * Get the ident for a path, fetching from API if not cached
     */
    @Throws(IOException::class)
    fun getIdent(
        path: KraPath,
        client: KraApiClient
    ): String? {
        val pathStr = path.toString()

        // Root path has no ident
        if (pathStr == "/" || pathStr.isEmpty()) {
            return null
        }

        val cacheKey = CacheKey(path.fileSystem.authority, pathStr)

        // Check cache first
        cache[cacheKey]?.let { return it }

        // Not in cache, need to resolve it
        val ident = resolvePathToIdent(path, client)

        // Cache the result
        if (ident != null) {
            cache[cacheKey] = ident
        }

        return ident
    }

    /**
     * Cache an ident for a path (used when we know the mapping from API responses)
     */
    fun putIdent(path: KraPath, ident: String) {
        val pathStr = path.toString()
        if (pathStr.isEmpty() || pathStr == "/") {
            return // Don't cache root
        }

        val cacheKey = CacheKey(path.fileSystem.authority, pathStr)
        cache[cacheKey] = ident
    }

    /**
     * Remove a path from cache (used when file is deleted)
     */
    fun removeIdent(path: KraPath) {
        val pathStr = path.toString()
        val cacheKey = CacheKey(path.fileSystem.authority, pathStr)
        cache.remove(cacheKey)
    }

    /**
     * Invalidate directory cache - removes all cached entries for paths under this directory
     * This is needed when directory contents change (files added/removed)
     * Note: This does NOT remove the directory itself, only its children
     */
    fun invalidateDirectory(path: KraPath) {
        val pathStr = path.toString()
        val normalizedPath = if (pathStr.endsWith("/")) pathStr else "$pathStr/"
        val authority = path.fileSystem.authority

        // Remove all cache entries for children (but not the directory itself)
        cache.keys.removeIf { key ->
            key.authority == authority &&
            key.path.startsWith(normalizedPath) &&
            key.path != pathStr
        }
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

    /**
     * Resolve a path to its ident by walking the path tree
     */
    @Throws(IOException::class)
    private fun resolvePathToIdent(
        path: KraPath,
        client: KraApiClient
    ): String? {
        // Break path into segments
        val segments = mutableListOf<String>()
        var current: KraPath? = path
        while (current != null && current.nameCount > 0) {
            val name = current.fileName?.toString()
            if (name != null) {
                segments.add(0, name) // Add to beginning
            }
            current = current.parent
        }

        if (segments.isEmpty()) {
            return null // Root path
        }

        // Walk through segments, listing each level
        var currentIdent: String? = null // null = root
        var currentPath = path.root ?: path.fileSystem.rootDirectory

        for (segment in segments) {
            // List files at current level
            val files = try {
                client.listFiles(currentIdent)
            } catch (e: Exception) {
                throw IOException("Failed to list files at: $currentPath", e)
            }

            // Find the segment in the list
            val file = files.find { it.name == segment }
                ?: throw IOException("File not found: $segment in $currentPath")

            currentIdent = file.ident
            currentPath = currentPath.resolve(segment)

            // Cache intermediate paths too
            val intermediateCacheKey = CacheKey(
                path.fileSystem.authority,
                currentPath.toString()
            )
            cache[intermediateCacheKey] = currentIdent
        }

        return currentIdent
    }

    /**
     * Prefetch idents for a list of files (optimization for directory listing)
     */
    fun prefetchIdents(
        parentPath: KraPath,
        files: List<Pair<String, String>> // (name, ident) pairs
    ) {
        val authority = parentPath.fileSystem.authority
        val parentPathStr = parentPath.toString()

        for ((name, ident) in files) {
            val childPath = if (parentPathStr == "/") {
                "/$name"
            } else {
                "$parentPathStr/$name"
            }

            val cacheKey = CacheKey(authority, childPath)
            cache[cacheKey] = ident
        }
    }
}
