/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

internal class KraProgressRequestBody(
    private val delegate: RequestBody,
    private val contentLength: Long,
    private val progressListener: ((uploaded: Long, total: Long) -> Unit)?
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val countingSink = object : ForwardingSink(sink) {
            var bytesWritten = 0L
            var lastProgressTime = System.currentTimeMillis()

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount

                // Report progress every 500ms
                val now = System.currentTimeMillis()
                if (now - lastProgressTime >= 500) {
                    progressListener?.invoke(bytesWritten, contentLength)
                    lastProgressTime = now
                }
            }
        }

        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()

        // Final progress update (100%)
        progressListener?.invoke(contentLength, contentLength)
    }
}
