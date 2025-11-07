/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */

package sk.kra.files.provider.kra

import java8.nio.file.Path
import java8.nio.file.StandardOpenOption
import sk.kra.files.provider.common.CopyOptions
import sk.kra.files.provider.common.newInputStream
import sk.kra.files.provider.common.newOutputStream
import sk.kra.files.provider.common.UploadStateManager
import sk.kra.files.provider.kra.client.KraApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream

/**
 * Custom copy/move implementation for KRA provider with progress reporting support
 */
internal object KraCopyMove {

    // Removed DEFAULT_CHUNK_SIZE - now using AdaptiveChunkSizer for dynamic chunk sizing

    /**
     * Copy from KRA to foreign file system (e.g. KRA → Local)
     * Uses stream-to-stream copy with progress reporting
     */
    @Throws(IOException::class)
    fun copyToForeign(
        source: KraPath,
        target: Path,
        copyOptions: CopyOptions,
        client: KraApiClient
    ) {
        // Get source file info
        val sourceIdent = source.getIdentWithClient(client)
            ?: throw IOException("Cannot get ident for source: $source")

        val fileInfo = client.getFileInfo(sourceIdent)

        if (fileInfo.folder) {
            throw IOException("Directory copy not yet supported: $source")
        }

        // Get download link
        val downloadInfo = client.getDownloadLink(sourceIdent)

        // Download stream
        val inputStream = client.downloadFile(downloadInfo.link)

        try {
            // Create target output stream
            val outputStream = target.newOutputStream(
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )

            var successful = false
            try {
                // Copy with progress
                inputStream.copyTo(
                    outputStream,
                    copyOptions.progressIntervalMillis,
                    copyOptions.progressListener
                )
                successful = true
            } finally {
                try {
                    outputStream.close()
                } finally {
                    if (!successful) {
                        try {
                            target.toFile().delete()
                        } catch (e: Exception) {
                            android.util.Log.w("KraCopyMove", "Failed to delete target after error", e)
                        }
                    }
                }
            }
        } finally {
            inputStream.close()
        }
    }

    /**
     * Copy from foreign file system to KRA (e.g. Local → KRA)
     * Uses chunked TUS upload with progress reporting
     */
    @Throws(IOException::class)
    fun copyFromForeign(
        source: Path,
        target: KraPath,
        copyOptions: CopyOptions,
        client: KraApiClient
    ) {
        val fileName = target.fileName?.toString()
            ?: throw IOException("Target path must have a filename: $target")
        val parent = target.parent?.getIdentWithClient(client)

        // Check if file already exists and delete it
        try {
            val existingIdent = target.getIdentWithClient(client)
            if (existingIdent != null) {
                client.deleteFile(existingIdent)
            }
        } catch (e: Exception) {
	    //
        }

        // Create file entry on KRA
        val createInfo = client.createFile(
            name = fileName,
            isFolder = false,
            parent = parent,
            shared = false
        )

        val uploadUrl = createInfo.link ?: throw IOException("No upload URL available for: $target")

        // Notify UI immediately so file appears in list (with 0 bytes initially)
        target.parent?.let { KraPathIdentCache.invalidateDirectory(it) }
        sk.kra.files.provider.common.LocalWatchService.onEntryCreated(target)

        // Open source input stream
        val inputStream = source.newInputStream()

        try {
            // Get file size for TUS Upload-Length header
            val fileSize = source.toFile().length()

            // Register upload with UploadStateManager for inline progress display
            UploadStateManager.updateProgress(target, 0, fileSize)

            // Upload with progress
            uploadWithProgress(
                inputStream = inputStream,
                uploadUrl = uploadUrl,
                uploadIdent = createInfo.ident,
                totalSize = fileSize,
                progressIntervalMillis = copyOptions.progressIntervalMillis,
                progressListener = copyOptions.progressListener,
                target = target,
                client = client
            )

            // Remove upload tracking first (before notifying UI)
            UploadStateManager.removeUpload(target)

            // Invalidate cache and notify UI that file was modified (size updated)
            target.parent?.let { KraPathIdentCache.invalidateDirectory(it) }
            sk.kra.files.provider.common.LocalWatchService.onEntryModified(target)

            // Start archived polling
            startArchivedPolling(createInfo.ident, target, client)

        } catch (e: Exception) {
            // Remove upload tracking on error
            UploadStateManager.removeUpload(target)

            // Clean up created file on error
            try {
                client.deleteFile(createInfo.ident)
            } catch (cleanupError: Exception) {
                android.util.Log.w("KraCopyMove", "Failed to clean up after error", cleanupError)
            }

            // Notify UI that file should be removed
            target.parent?.let { KraPathIdentCache.invalidateDirectory(it) }
            sk.kra.files.provider.common.LocalWatchService.onEntryDeleted(target)

            // Re-throw InterruptedIOException as-is so MaterialFiles knows the operation was cancelled
            if (e is java.io.InterruptedIOException) {
                throw e
            }
            throw IOException("Failed to upload file to KRA: $target", e)
        } finally {
            inputStream.close()
        }
    }

    /**
     * Upload file with chunked TUS protocol and progress reporting
     */
    @Throws(IOException::class)
    private fun uploadWithProgress(
        inputStream: InputStream,
        uploadUrl: String,
        uploadIdent: String,
        totalSize: Long,
        progressIntervalMillis: Long,
        progressListener: ((Long) -> Unit)?,
        target: KraPath,
        client: KraApiClient
    ) {
        // Add ident as query parameter if not already present
        val url = if (uploadUrl.contains("?")) {
            uploadUrl
        } else {
            "$uploadUrl?ident=$uploadIdent"
        }

        // Step 1: Create TUS session with POST
        val tusLocationUrl = createTusSession(url, uploadIdent, totalSize, client)

        // Step 2: Upload data in chunks with PATCH using adaptive chunk sizing
        val chunkSizer = AdaptiveChunkSizer()
        var buffer = ByteArray(chunkSizer.getCurrentChunkSize())
        var uploadedBytes = 0L
        var lastProgressTime = System.currentTimeMillis()
        var sinceLastReport = 0L

        while (true) {
            val currentChunkSize = chunkSizer.getCurrentChunkSize()

            // Read up to currentChunkSize bytes
            val readSize = inputStream.read(buffer, 0, minOf(currentChunkSize, buffer.size))
            if (readSize == -1) break

            // Upload this chunk
            uploadChunk(tusLocationUrl, buffer, readSize, uploadedBytes, client)

            uploadedBytes += readSize
            sinceLastReport += readSize

            // Update chunk size based on measured speed
            val nextChunkSize = chunkSizer.updateAndGetNextChunkSize(uploadedBytes)
            if (nextChunkSize != buffer.size) {
                buffer = ByteArray(nextChunkSize)
            }

            // Report progress
            val now = System.currentTimeMillis()
            if (progressListener != null && now >= lastProgressTime + progressIntervalMillis) {
                progressListener(sinceLastReport)
                lastProgressTime = now
                sinceLastReport = 0
            }

            // Update inline progress bar state
            UploadStateManager.updateProgress(target, uploadedBytes, totalSize)
        }

        // Final progress report
        if (sinceLastReport > 0) {
            progressListener?.invoke(sinceLastReport)
        }

        // Final inline progress update
        UploadStateManager.updateProgress(target, uploadedBytes, totalSize)
    }

    /**
     * Create TUS upload session (POST request)
     * Returns the Location URL for uploading chunks
     */
    @Throws(IOException::class)
    private fun createTusSession(
        uploadUrl: String,
        uploadIdent: String,
        totalSize: Long,
        client: KraApiClient
    ): String {
        // TUS Upload-Metadata format: "ident base64(ident_value)"
        val identBase64 = android.util.Base64.encodeToString(
            uploadIdent.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val uploadMetadata = "ident $identBase64"

        val createRequest = okhttp3.Request.Builder()
            .url(uploadUrl)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .addHeader("Upload-Length", totalSize.toString())
            .addHeader("Upload-Metadata", uploadMetadata)
            .addHeader("Tus-Resumable", "1.0.0")
            .build()

        val createResponse = client.getHttpClient().newCall(createRequest).execute()

        if (!createResponse.isSuccessful) {
            val errorMessage = try {
                createResponse.body?.string() ?: "No error details"
            } catch (e: Exception) {
                "Unable to read error: ${e.message}"
            }
            throw IOException("TUS session creation failed: ${createResponse.code} - $errorMessage")
        }

        // Get Location header
        val location = createResponse.header("Location")
            ?: throw IOException("No Location header in TUS create response")

        // Convert relative to absolute URL
        return if (location.startsWith("http")) {
            location
        } else {
            val baseUri = java.net.URI(uploadUrl)
            val absoluteUri = baseUri.resolve(location)
            absoluteUri.toString()
        }
    }

    /**
     * Upload a single chunk with TUS PATCH request
     */
    @Throws(IOException::class)
    private fun uploadChunk(
        tusLocationUrl: String,
        data: ByteArray,
        size: Int,
        offset: Long,
        client: KraApiClient
    ) {
        val chunkData = if (size == data.size) {
            data
        } else {
            data.copyOf(size)
        }

        val requestBody = chunkData.toRequestBody(
            "application/offset+octet-stream".toMediaType()
        )

        val patchRequest = okhttp3.Request.Builder()
            .url(tusLocationUrl)
            .patch(requestBody)
            .addHeader("Upload-Offset", offset.toString())
            .addHeader("Content-Type", "application/offset+octet-stream")
            .addHeader("Tus-Resumable", "1.0.0")
            .build()

        val patchResponse = client.getHttpClient().newCall(patchRequest).execute()

        if (!patchResponse.isSuccessful) {
            val errorMessage = try {
                patchResponse.body?.string() ?: "No error details"
            } catch (e: Exception) {
                "Unable to read error: ${e.message}"
            }
            throw IOException("TUS chunk upload failed: ${patchResponse.code} - $errorMessage")
        }
    }

    /**
     * Start background polling for archived status
     */
    private fun startArchivedPolling(
        ident: String,
        path: KraPath,
        client: KraApiClient
    ) {
        Thread {
            try {
                var attempts = 0
                val maxAttempts = 60  // Max 5 minutes

                while (attempts < maxAttempts) {
                    Thread.sleep(5000)  // Wait 5 seconds
                    attempts++

                    try {
                        val fileInfo = client.getFileInfo(ident)

                        if (fileInfo.archived == true) {

                            // Invalidate cache
                            path.parent?.let { KraPathIdentCache.invalidateDirectory(it) }

                            // Notify UI
                            path.parent?.let {
                                sk.kra.files.provider.common.LocalWatchService.onEntryModified(it)
                            }
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("KraCopyMove", "startArchivedPolling: error", e)
                    }
                }

                if (attempts >= maxAttempts) {
                    android.util.Log.w("KraCopyMove", "startArchivedPolling: max attempts reached")
                }
            } catch (e: InterruptedException) {
                android.util.Log.w("KraCopyMove", "startArchivedPolling: interrupted", e)
            }
        }.start()
    }

    /**
     * Extension function for InputStream.copyTo with progress
     * Uses fixed chunk size for downloads (KRA → Local)
     */
    private fun InputStream.copyTo(
        outputStream: java.io.OutputStream,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ) {
        val downloadChunkSize = 512 * 1024 // 512 KB for downloads
        val buffer = ByteArray(downloadChunkSize)
        var lastProgressMillis = System.currentTimeMillis()
        var copiedSize = 0L

        while (true) {
            val readSize = read(buffer)
            if (readSize == -1) {
                break
            }
            outputStream.write(buffer, 0, readSize)
            copiedSize += readSize.toLong()

            val currentTimeMillis = System.currentTimeMillis()
            if (listener != null && currentTimeMillis >= lastProgressMillis + intervalMillis) {
                listener(copiedSize)
                lastProgressMillis = currentTimeMillis
                copiedSize = 0
            }
        }
        listener?.invoke(copiedSize)
    }
}
