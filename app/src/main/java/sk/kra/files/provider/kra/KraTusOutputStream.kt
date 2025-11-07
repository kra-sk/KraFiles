/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import sk.kra.files.provider.common.LocalWatchService
import sk.kra.files.provider.kra.client.KraApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * OutputStream that uploads to KRA using TUS protocol with real-time progress
 * Uses piped streams to make write() block during actual upload
 */
internal class KraTusOutputStream(
    private val path: KraPath,
    private val client: KraApiClient,
    private val uploadUrl: String,
    private val uploadIdent: String
) : OutputStream() {

    private val pipedOutput = PipedOutputStream()
    private val pipedInput = PipedInputStream(pipedOutput, 65536) // 64KB buffer
    private val uploadThread: Thread
    private val uploadError = AtomicReference<Throwable?>()
    private var closed = false

    init {
        // Start background thread that reads from pipe and uploads
        uploadThread = Thread {
            try {
                performUpload()
            } catch (e: Throwable) {
                android.util.Log.e("KraTusOutputStream", "Upload thread error", e)
                uploadError.set(e)
                try {
                    pipedInput.close()
                } catch (closeError: Exception) {
                    // Ignore
                }
            }
        }
        uploadThread.start()
    }

    override fun write(b: Int) {
        checkError()
        pipedOutput.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        checkError()
        pipedOutput.write(b, off, len)
    }

    override fun flush() {
        checkError()
        pipedOutput.flush()
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true

        try {
            // Close pipe to signal end of data
            pipedOutput.close()

            // Wait for upload to complete
            uploadThread.join()

            checkError()

            // Invalidate cache and notify UI
            path.parent?.let { KraPathIdentCache.invalidateDirectory(it) }
            LocalWatchService.onEntryCreated(path)

            // Start archived polling
            startArchivedPolling()

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Upload interrupted", e)
        }
    }

    private fun performUpload() {
        // Read all data from pipe first to determine size
        val buffer = mutableListOf<ByteArray>()
        val chunkSize = 8192
        val readBuffer = ByteArray(chunkSize)
        var totalSize = 0

        while (true) {
            val bytesRead = pipedInput.read(readBuffer)
            if (bytesRead == -1) break

            val chunk = readBuffer.copyOf(bytesRead)
            buffer.add(chunk)
            totalSize += bytesRead
        }

        // Create TUS session
        val tusLocationUrl = createTusSession(totalSize.toLong())

        // Upload all chunks
        var uploadedBytes = 0
        for (chunk in buffer) {
            uploadChunk(tusLocationUrl, chunk, uploadedBytes.toLong())
            uploadedBytes += chunk.size
        }
    }

    @Throws(IOException::class)
    private fun createTusSession(totalSize: Long): String {
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
            throw IOException("TUS create failed: ${createResponse.code} - $errorMessage")
        }

        val location = createResponse.header("Location")
            ?: throw IOException("No Location header in TUS create response")

        return if (location.startsWith("http")) {
            location
        } else {
            val baseUri = java.net.URI(uploadUrl)
            baseUri.resolve(location).toString()
        }
    }

    @Throws(IOException::class)
    private fun uploadChunk(tusLocationUrl: String, data: ByteArray, offset: Long) {
        val requestBody = data.toRequestBody("application/offset+octet-stream".toMediaType())

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
            throw IOException("TUS upload failed: ${patchResponse.code} - $errorMessage")
        }
    }

    private fun startArchivedPolling() {
        Thread {
            try {
                var attempts = 0
                val maxAttempts = 60  // Max 5 minutes

                while (attempts < maxAttempts) {
                    Thread.sleep(5000)  // Wait 5 seconds
                    attempts++

                    try {
                        val fileInfo = client.getFileInfo(uploadIdent)

                        if (fileInfo.archived == true) {

                            // Invalidate cache
                            path.parent?.let { KraPathIdentCache.invalidateDirectory(it) }

                            // Notify UI
                            path.parent?.let {
                                LocalWatchService.onEntryModified(it)
                            }
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("KraTusOutputStream", "startArchivedPolling: error", e)
                    }
                }

                if (attempts >= maxAttempts) {
                    android.util.Log.w("KraTusOutputStream", "startArchivedPolling: max attempts reached")
                }
            } catch (e: InterruptedException) {
                android.util.Log.w("KraTusOutputStream", "startArchivedPolling: interrupted", e)
            }
        }.start()
    }

    private fun checkError() {
        uploadError.get()?.let {
            throw IOException("Upload failed", it)
        }
    }
}
