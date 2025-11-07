/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */

package sk.kra.files.provider.kra

import java8.nio.channels.SeekableByteChannel
import sk.kra.files.provider.common.LocalWatchService
import sk.kra.files.provider.kra.client.KraApiClient
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.NonReadableChannelException

/**
 * ByteChannel implementation for KRA file operations
 * Supports both reading (download) and writing (upload)
 */
internal class KraByteChannel(
    private val path: KraPath,
    private val client: KraApiClient,
    private val read: Boolean,
    private val write: Boolean,
    private val progressListener: ((Long) -> Unit)? = null
) : SeekableByteChannel {

    private var isOpen = true
    private var position: Long = 0

    // For reading (download)
    private var inputStream: InputStream? = null
    private var downloadUrl: String? = null
    private var fileSize: Long = 0

    // For writing (upload) - Buffered TUS upload
    private var uploadIdent: String? = null
    private var uploadBaseUrl: String? = null  // Base URL from createFile
    private val writeBuffer = mutableListOf<ByteArray>()  // Buffer for write data

    init {
        if (read && write) {
            throw IllegalArgumentException("Cannot open channel for both read and write")
        }
        if (!read && !write) {
            throw IllegalArgumentException("Must specify read or write mode")
        }

        if (read) {
            initializeRead()
        } else {
            initializeWrite()
        }
    }

    @Throws(IOException::class)
    private fun initializeRead() {
        val ident = path.getIdentWithClient(client) ?: throw IOException("Invalid path for KRA file: $path")

        try {
            // Get file info to verify it exists and get size
            val fileInfo = client.getFileInfo(ident)
            if (fileInfo.folder) {
                throw IOException("Cannot read from a folder: $path")
            }
            fileSize = fileInfo.size ?: 0L

            // Get download link
            val downloadInfo = client.getDownloadLink(ident)
            downloadUrl = downloadInfo.link

            // Initialize input stream
            inputStream = client.downloadFile(downloadUrl!!)
        } catch (e: Exception) {
            throw IOException("Failed to initialize read for: $path", e)
        }
    }

    @Throws(IOException::class)
    private fun initializeWrite() {
        val fileName = path.fileName?.toString()
            ?: throw IOException("Path must have a filename: $path")
        val parent = path.parent?.getIdentWithClient(client)

        try {
            // Check if file already exists and delete it
            // This is necessary because upload links expire for existing files
            try {
                val existingIdent = path.getIdentWithClient(client)
                if (existingIdent != null) {
                    client.deleteFile(existingIdent)
                }
            } catch (e: Exception) {
                // File doesn't exist, ignore
            }

            // Create file entry on KRA
            val createInfo = client.createFile(
                name = fileName,
                isFolder = false,
                parent = parent,
                shared = false
            )
            uploadIdent = createInfo.ident
            uploadBaseUrl = createInfo.link  // Save upload URL from create response
        } catch (e: Exception) {
            throw IOException("Failed to initialize write for: $path", e)
        }
    }

    @Throws(IOException::class)
    override fun read(dst: ByteBuffer): Int {
        checkOpen()
        if (!read) {
            throw NonReadableChannelException()
        }

        val stream = inputStream ?: throw IOException("Input stream not initialized")

        val bytesToRead = dst.remaining()
        if (bytesToRead == 0) {
            return 0
        }

        // Read from input stream into byte array
        val buffer = ByteArray(bytesToRead)
        val bytesRead = try {
            stream.read(buffer, 0, bytesToRead)
        } catch (e: Exception) {
            throw IOException("Failed to read from KRA file: $path", e)
        }

        if (bytesRead == -1) {
            return -1 // EOF
        }

        // Put bytes into ByteBuffer
        dst.put(buffer, 0, bytesRead)
        position += bytesRead

        return bytesRead
    }

    @Throws(IOException::class)
    override fun write(src: ByteBuffer): Int {
        checkOpen()
        if (!write) {
            throw NonWritableChannelException()
        }

        val bytesToWrite = src.remaining()
        if (bytesToWrite == 0) {
            return 0
        }

        // Copy bytes from ByteBuffer to byte array
        val buffer = ByteArray(bytesToWrite)
        src.get(buffer)

        // Add to write buffer (will be uploaded on close)
        writeBuffer.add(buffer)
        position += bytesToWrite

        return bytesToWrite
    }

    override fun position(): Long {
        checkOpen()
        return position
    }

    @Throws(IOException::class)
    override fun position(newPosition: Long): SeekableByteChannel {
        checkOpen()
        if (newPosition < 0) {
            throw IllegalArgumentException("Position cannot be negative: $newPosition")
        }

        if (read) {
            // For reading, we need to reset the stream and skip to position
            // This is inefficient but necessary for random access
            if (newPosition < position) {
                // Need to restart download
                inputStream?.close()
                inputStream = client.downloadFile(downloadUrl!!)
                position = 0
            }

            // Skip to desired position
            val toSkip = newPosition - position
            if (toSkip > 0) {
                var remaining = toSkip
                while (remaining > 0) {
                    val skipped = inputStream!!.skip(remaining)
                    if (skipped <= 0) {
                        throw IOException("Failed to skip to position: $newPosition")
                    }
                    remaining -= skipped
                    position += skipped
                }
            }
        } else {
            // For writing, just update position
            // Note: This doesn't actually support random write access
            position = newPosition
        }

        return this
    }

    override fun size(): Long {
        checkOpen()
        return if (read) {
            fileSize
        } else {
            position
        }
    }

    @Throws(IOException::class)
    override fun truncate(size: Long): SeekableByteChannel {
        checkOpen()
        if (!write) {
            throw NonWritableChannelException()
        }
        if (size < 0) {
            throw IllegalArgumentException("Size cannot be negative: $size")
        }

        // For simplicity, truncate is not supported for KRA uploads
        // Would require complex buffer management
        throw UnsupportedOperationException("Truncate not supported for KRA uploads")
    }

    override fun isOpen(): Boolean = isOpen

    @Throws(IOException::class)
    override fun close() {
        if (!isOpen) {
            return
        }

        try {
            if (read) {
                inputStream?.close()
            } else {
                // Upload all buffered data
                performUpload()

                // Invalidate parent directory cache so the new file appears
                path.parent?.let { KraPathIdentCache.invalidateDirectory(it) }

                // Notify watch service immediately (file will show with 0B)
                LocalWatchService.onEntryCreated(path)

                // Start background polling for archived status
                startArchivedPolling()
            }
        } finally {
            isOpen = false
            inputStream = null
            writeBuffer.clear()
        }
    }

    @Throws(IOException::class)
    private fun performUpload() {
        val ident = uploadIdent ?: throw IOException("Upload ident not initialized")

        if (writeBuffer.isEmpty()) {
            // Empty file, nothing to upload
            return
        }

        try {
            val baseUrl = uploadBaseUrl ?: throw IOException("No upload URL available for: $path")

            // Add ident as query parameter if not already present
            val url = if (baseUrl.contains("?")) {
                baseUrl
            } else {
                "$baseUrl?ident=$ident"
            }

            // Combine all write buffers
            val totalSize = writeBuffer.sumOf { it.size }
            val data = ByteArray(totalSize)
            var offset = 0
            for (buffer in writeBuffer) {
                System.arraycopy(buffer, 0, data, offset, buffer.size)
                offset += buffer.size
            }

            // Upload data using TUS protocol
            uploadData(url, data)

        } catch (e: Exception) {
            throw IOException("Failed to upload file to KRA: $path", e)
        }
    }

    @Throws(IOException::class)
    private fun uploadData(uploadUrl: String, data: ByteArray) {
        // Implement TUS protocol for resumable uploads
        try {
            val ident = uploadIdent ?: throw IOException("No upload ident available")

            // Step 1: Create TUS upload session with POST
            val identBase64 = android.util.Base64.encodeToString(
                ident.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val uploadMetadata = "ident $identBase64"

            val createRequest = okhttp3.Request.Builder()
                .url(uploadUrl)
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .addHeader("Upload-Length", data.size.toString())
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
                throw IOException("TUS create failed: ${createResponse.code} - $errorMessage")
            }

            // Get Location header for upload URL
            val location = createResponse.header("Location")
                ?: throw IOException("No Location header in TUS create response")

            // Location can be relative or absolute - make it absolute
            val locationUrl = if (location.startsWith("http")) {
                location
            } else {
                val baseUri = java.net.URI(uploadUrl)
                val absoluteUri = baseUri.resolve(location)
                absoluteUri.toString()
            }

            // Step 2: Upload data with PATCH to Location URL
            val baseRequestBody = okhttp3.RequestBody.create(
                "application/offset+octet-stream".toMediaType(),
                data
            )

            // Wrap with progress reporting
            val requestBody = if (progressListener != null) {
                KraProgressRequestBody(baseRequestBody, data.size.toLong()) { uploaded, total ->
                    // Report progress by calling the listener with uploaded bytes
                    progressListener.invoke(uploaded)

                    // Also notify UI to update
                    LocalWatchService.onEntryModified(path)
                }
            } else {
                baseRequestBody
            }

            val patchRequest = okhttp3.Request.Builder()
                .url(locationUrl)
                .patch(requestBody)
                .addHeader("Upload-Offset", "0")
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
                throw IOException("TUS upload failed: ${patchResponse.code} - $errorMessage")
            }

        } catch (e: Exception) {
            throw IOException("Failed to upload data to: $uploadUrl", e)
        }
    }

    private fun startArchivedPolling() {
        val ident = uploadIdent ?: return

        // Start background thread to poll for archived status
        Thread {
            try {
                var attempts = 0
                val maxAttempts = 60  // Max 5 minutes (60 * 5 seconds)

                while (attempts < maxAttempts) {
                    Thread.sleep(5000)  // Wait 5 seconds between checks
                    attempts++

                    try {
                        val fileInfo = client.getFileInfo(ident)

                        if (fileInfo.archived == true) {
                            // File is archived, notify UI

                            // Invalidate cache for parent directory
                            path.parent?.let { KraPathIdentCache.invalidateDirectory(it) }

                            // Notify on parent directory to trigger refresh
                            path.parent?.let { LocalWatchService.onEntryModified(it) }
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("KraByteChannel", "startArchivedPolling: error checking archived status", e)
                        // Continue polling even if there's an error
                    }
                }

                if (attempts >= maxAttempts) {
                    android.util.Log.w("KraByteChannel", "startArchivedPolling: max attempts reached, giving up")
                }
            } catch (e: InterruptedException) {
                android.util.Log.w("KraByteChannel", "startArchivedPolling: interrupted", e)
            }
        }.start()
    }

    @Throws(ClosedChannelException::class)
    private fun checkOpen() {
        if (!isOpen) {
            throw ClosedChannelException()
        }
    }
}
