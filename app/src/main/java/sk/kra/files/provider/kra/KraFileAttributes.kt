/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import android.os.Parcelable
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.Locale
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import sk.kra.files.provider.common.AbstractBasicFileAttributes
import sk.kra.files.provider.common.BasicFileType
import sk.kra.files.provider.common.EPOCH
import sk.kra.files.provider.common.FileTimeParceler
import sk.kra.files.provider.kra.client.FileInfo

@Parcelize
internal data class KraFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: BasicFileType,
    override val size: Long,
    override val fileKey: Parcelable,
    val isShared: Boolean,
    val isPasswordProtected: Boolean,
    val ident: String?
) : AbstractBasicFileAttributes() {

    companion object {
        fun from(fileInfo: FileInfo, path: KraPath): KraFileAttributes {
            val lastModifiedTime = parseUnixTimestamp(fileInfo.created)
            val lastAccessTime = lastModifiedTime
            val creationTime = lastModifiedTime
            val type = if (fileInfo.folder) BasicFileType.DIRECTORY else BasicFileType.REGULAR_FILE
            val size = fileInfo.size ?: 0L
            val fileKey = path
            val isShared = fileInfo.shared ?: false
            val isPasswordProtected = fileInfo.password ?: false
            val ident = fileInfo.ident

            return KraFileAttributes(
                lastModifiedTime,
                lastAccessTime,
                creationTime,
                type,
                size,
                fileKey,
                isShared,
                isPasswordProtected,
                ident
            )
        }

	private fun parseUnixTimestamp(timestamp: Long?): FileTime {
            if (timestamp == null) {
                return FileTime::class.EPOCH
            }
            return try {
		FileTime.from(timestamp, TimeUnit.SECONDS)
            } catch (e: Exception) {
                FileTime::class.EPOCH
            }
        }
    }
}
