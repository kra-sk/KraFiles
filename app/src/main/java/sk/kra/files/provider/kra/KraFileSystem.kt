/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchService
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import sk.kra.files.provider.common.ByteString
import sk.kra.files.provider.common.ByteStringBuilder
import sk.kra.files.provider.common.ByteStringListPathCreator
import sk.kra.files.provider.common.LocalWatchService
import sk.kra.files.provider.common.toByteString
import sk.kra.files.provider.kra.client.KraAuthority
import sk.kra.files.util.readParcelable
import java.io.IOException

internal class KraFileSystem(
    private val provider: KraFileSystemProvider,
    val authority: KraAuthority
) : FileSystem(), ByteStringListPathCreator, Parcelable {

    val rootDirectory = KraPath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute) {
            throw AssertionError("Root directory must be absolute")
        }
        if (rootDirectory.nameCount != 0) {
            throw AssertionError("Root directory must contain no names")
        }
    }

    private val lock = Any()

    private var isOpen = true

    val defaultDirectory: KraPath
        get() = rootDirectory

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) {
                return
            }
            provider.removeFileSystem(this)
            isOpen = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> =
        setOf("basic")

    override fun getPath(first: String, vararg more: String): KraPath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return KraPath(this, path)
    }

    override fun getPath(first: ByteString, vararg more: ByteString): KraPath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return KraPath(this, path)
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw UnsupportedOperationException()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newWatchService(): WatchService = LocalWatchService()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as KraFileSystem
        return authority == other.authority
    }

    override fun hashCode(): Int = authority.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(authority, flags)
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()
        private val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()
        private const val SEPARATOR_STRING = SEPARATOR.toInt().toChar().toString()

        @JvmField
        val CREATOR = object : Parcelable.Creator<KraFileSystem> {
            override fun createFromParcel(source: Parcel): KraFileSystem {
                val authority = source.readParcelable<KraAuthority>()!!
                return KraFileSystemProvider.getOrNewFileSystem(authority)
            }

            override fun newArray(size: Int): Array<KraFileSystem?> = arrayOfNulls(size)
        }
    }
}
