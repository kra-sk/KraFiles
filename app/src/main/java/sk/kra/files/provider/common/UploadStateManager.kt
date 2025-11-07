package sk.kra.files.provider.common

import java8.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global singleton for tracking upload progress across the application
 * Allows UI components to observe upload status and display inline progress bars
 */
object UploadStateManager {
    private val _uploadStates = MutableStateFlow<Map<Path, UploadState>>(emptyMap())
    val uploadStates: StateFlow<Map<Path, UploadState>> = _uploadStates.asStateFlow()

    /**
     * Update upload progress for a specific path
     *
     * @param path The path being uploaded
     * @param bytesUploaded Number of bytes uploaded so far
     * @param totalBytes Total size of the file in bytes
     */
    fun updateProgress(path: Path, bytesUploaded: Long, totalBytes: Long) {
        _uploadStates.value = _uploadStates.value + (path to UploadState(bytesUploaded, totalBytes))
    }

    /**
     * Remove upload tracking for a path (called when upload completes or fails)
     *
     * @param path The path to stop tracking
     */
    fun removeUpload(path: Path) {
        _uploadStates.value = _uploadStates.value.filterKeys { it != path }
    }

    /**
     * Check if a path is currently being uploaded
     *
     * @param path The path to check
     * @return true if the path is being uploaded, false otherwise
     */
    fun isUploading(path: Path): Boolean {
        return _uploadStates.value.containsKey(path)
    }

    /**
     * Get upload state for a specific path
     *
     * @param path The path to get state for
     * @return UploadState if uploading, null otherwise
     */
    fun getUploadState(path: Path): UploadState? {
        return _uploadStates.value[path]
    }
}

/**
 * Represents the current upload state for a file
 *
 * @property bytesUploaded Number of bytes uploaded so far
 * @property totalBytes Total size of the file in bytes
 */
data class UploadState(
    val bytesUploaded: Long,
    val totalBytes: Long
) {
    /**
     * Calculate upload progress as percentage (0-100)
     */
    val progressPercent: Int
        get() = if (totalBytes > 0) {
            ((bytesUploaded * 100) / totalBytes).toInt()
        } else {
            0
        }
}
