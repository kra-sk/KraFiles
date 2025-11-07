/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */

package sk.kra.files.provider.kra

import android.util.Log

/**
 * Dynamically adjusts chunk size based on upload speed for optimal performance
 */
internal class AdaptiveChunkSizer(
    initialChunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    companion object {
        private const val MIN_CHUNK_SIZE = 64 * 1024      // 64 KB
        private const val DEFAULT_CHUNK_SIZE = 512 * 1024 // 512 KB (higher start for fast networks)
        private const val MAX_CHUNK_SIZE = 16 * 1024 * 1024 // 16 MB (for gigabit networks!)

        // Thresholds optimized for high-speed networks (500 Mbps+)
        private const val ULTRA_HIGH_SPEED_THRESHOLD = 10 * 1024 * 1024L  // 10 MB/s (gigabit)
        private const val VERY_HIGH_SPEED_THRESHOLD = 5 * 1024 * 1024L   // 5 MB/s (very fast)
        private const val HIGH_SPEED_THRESHOLD = 2 * 1024 * 1024L  // 2 MB/s (fast)
        private const val MEDIUM_SPEED_THRESHOLD = 1024 * 1024L   // 1 MB/s (medium)
        private const val LOW_SPEED_THRESHOLD = 256 * 1024L    // 256 KB/s (slow)

        private const val SPEED_WINDOW_MS = 1000L  // 1 second (was 5s - much faster adaptation!)

        // Aggressive scaling factors for fast networks
        private const val SPEED_UP_ULTRA_FACTOR = 4.0f   // 4x for gigabit (10+ MB/s)
        private const val SPEED_UP_VERY_FAST_FACTOR = 3.0f  // 3x for very fast (5-10 MB/s)
        private const val SPEED_UP_FAST_FACTOR = 2.0f   // 2x for fast (2-5 MB/s)
        private const val SPEED_UP_MEDIUM_FACTOR = 1.5f // 1.5x for medium (1-2 MB/s)
        private const val SPEED_DOWN_FACTOR = 2.0f
    }

    private var currentChunkSize = initialChunkSize
    private var windowStartTime = System.currentTimeMillis()
    private var windowStartBytes = 0L

    fun getCurrentChunkSize(): Int = currentChunkSize

    /**
     * Updates chunk size based on measured upload speed
     * Should be called after each chunk upload
     *
     * @param totalUploadedBytes Total bytes uploaded so far
     * @param currentTimeMs Current time in milliseconds
     * @return The chunk size to use for the next upload
     */
    fun updateAndGetNextChunkSize(
        totalUploadedBytes: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Int {
        val elapsedMs = currentTimeMs - windowStartTime

        // Only adjust every SPEED_WINDOW_MS milliseconds
        if (elapsedMs >= SPEED_WINDOW_MS) {
            val bytesInWindow = totalUploadedBytes - windowStartBytes
            val bytesPerSecond = (bytesInWindow * 1000) / elapsedMs

            val newSize = when {
                bytesPerSecond > ULTRA_HIGH_SPEED_THRESHOLD -> {
                    // Ultra-fast gigabit connection (>10 MB/s) - quadruple chunk size!
                    val increased = (currentChunkSize * SPEED_UP_ULTRA_FACTOR).toInt()
                    minOf(increased, MAX_CHUNK_SIZE)
                }
                bytesPerSecond > VERY_HIGH_SPEED_THRESHOLD -> {
                    // Very fast connection (5-10 MB/s) - triple chunk size
                    val increased = (currentChunkSize * SPEED_UP_VERY_FAST_FACTOR).toInt()
                    minOf(increased, MAX_CHUNK_SIZE)
                }
                bytesPerSecond > HIGH_SPEED_THRESHOLD -> {
                    // Fast connection (2-5 MB/s) - double chunk size
                    val increased = (currentChunkSize * SPEED_UP_FAST_FACTOR).toInt()
                    minOf(increased, MAX_CHUNK_SIZE)
                }
                bytesPerSecond > MEDIUM_SPEED_THRESHOLD -> {
                    // Medium-fast connection (1-2 MB/s) - increase by 1.5x
                    val increased = (currentChunkSize * SPEED_UP_MEDIUM_FACTOR).toInt()
                    minOf(increased, MAX_CHUNK_SIZE)
                }
                bytesPerSecond < LOW_SPEED_THRESHOLD -> {
                    // Slow connection (<256 KB/s) - decrease chunk size
                    val decreased = (currentChunkSize / SPEED_DOWN_FACTOR).toInt()
                    maxOf(decreased, MIN_CHUNK_SIZE)
                }
                else -> {
                    // Medium speed (256 KB/s - 1 MB/s) - keep current size
                    currentChunkSize
                }
            }

            if (newSize != currentChunkSize) {
                currentChunkSize = newSize
            }

            // Reset measurement window
            windowStartTime = currentTimeMs
            windowStartBytes = totalUploadedBytes
        }

        return currentChunkSize
    }
}
