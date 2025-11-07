/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import sk.kra.files.provider.common.ByteString
import sk.kra.files.provider.common.ByteStringListPath
import sk.kra.files.provider.common.LocalWatchService
import sk.kra.files.provider.common.UriAuthority
import sk.kra.files.provider.kra.client.KraAuthority
import sk.kra.files.util.readParcelable
import java.io.File
import java.io.IOException

internal class KraPath : ByteStringListPath<KraPath> {
    internal val fileSystem: KraFileSystem

    constructor(
        fileSystem: KraFileSystem,
        path: ByteString
    ) : super(KraFileSystem.SEPARATOR, path) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: KraFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(KraFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == KraFileSystem.SEPARATOR

    override fun createPath(path: ByteString): KraPath = KraPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): KraPath =
        KraPath(fileSystem, absolute, segments)

    override val uriAuthority: UriAuthority
        get() = fileSystem.authority.toUriAuthority()

    override val defaultDirectory: KraPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): FileSystem = fileSystem

    override fun getRoot(): KraPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): KraPath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        if (watcher !is LocalWatchService) {
            throw ProviderMismatchException(watcher.toString())
        }
        return watcher.register(this, events, *modifiers)
    }

    // Get file ident from path - uses cache to map path to ident
    // This is internal and should not be exposed publicly
    internal fun getIdentWithClient(client: sk.kra.files.provider.kra.client.KraApiClient): String? {
        if (!isAbsolute || nameCount == 0) {
            return null // Root path
        }
        return try {
            KraPathIdentCache.getIdent(this, client)
        } catch (e: Exception) {
            null
        }
    }

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<KraPath> {
            override fun createFromParcel(source: Parcel): KraPath = KraPath(source)

            override fun newArray(size: Int): Array<KraPath?> = arrayOfNulls(size)
        }
    }
}

val Path.isKraPath: Boolean
    get() = this is KraPath

// Helper function to create KRA root path
internal fun KraAuthority.createKraRootPath(): KraPath =
    KraFileSystemProvider.getOrNewFileSystem(this).rootDirectory
