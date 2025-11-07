/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */

package sk.kra.files.provider.kra

import java8.nio.file.attribute.BasicFileAttributeView
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime
import java.io.IOException

internal class KraFileAttributeView(
    private val path: KraPath
) : BasicFileAttributeView {

    override fun name(): String = "basic"

    @Throws(IOException::class)
    override fun readAttributes(): BasicFileAttributes {
        return KraFileSystemProvider.readAttributes(path, BasicFileAttributes::class.java)
    }

    @Throws(IOException::class)
    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        // KRA API doesn't support setting file times
        // Silently ignore as per FileSystemProvider contract
    }
}
