/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessDeniedException
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.FileSystemAlreadyExistsException
import java8.nio.file.FileSystemNotFoundException
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import sk.kra.files.provider.common.CopyOptions
import sk.kra.files.provider.common.LocalWatchService
import sk.kra.files.provider.common.PathListDirectoryStream
import sk.kra.files.provider.common.PathObservable
import sk.kra.files.provider.common.PathObservableProvider
import sk.kra.files.provider.common.ProgressCopyOption
import sk.kra.files.provider.common.Searchable
import sk.kra.files.provider.common.WalkFileTreeSearchable
import sk.kra.files.provider.common.WatchServicePathObservable
import sk.kra.files.provider.common.decodedPathByteString
import sk.kra.files.provider.common.toCopyOptions
import sk.kra.files.provider.kra.client.FileInfo
import sk.kra.files.provider.kra.client.KraApiClient
import sk.kra.files.provider.kra.client.KraAuthority
import sk.kra.files.provider.kra.client.PasswordAuthentication
import sk.kra.files.settings.Settings
import sk.kra.files.storage.KraServer
import sk.kra.files.util.valueCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

object KraFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    private const val SCHEME = "kra"

    private val fileSystems = mutableMapOf<KraAuthority, KraFileSystem>()
    private val clients = mutableMapOf<KraAuthority, KraApiClient>()

    private val lock = Any()

    // ThreadLocal for passing progress listener from copy() to newOutputStream()
    private val progressListenerThreadLocal = ThreadLocal<((Long) -> Unit)?>()

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val authority = uri.kraAuthority
        synchronized(lock) {
            if (fileSystems[authority] != null) {
                throw FileSystemAlreadyExistsException(authority.toString())
            }
            return newFileSystemLocked(authority)
        }
    }

    internal fun getOrNewFileSystem(authority: KraAuthority): KraFileSystem =
        synchronized(lock) { fileSystems[authority] ?: newFileSystemLocked(authority) }

    private fun newFileSystemLocked(authority: KraAuthority): KraFileSystem {
        val fileSystem = KraFileSystem(this, authority)
        fileSystems[authority] = fileSystem
        return fileSystem
    }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val authority = uri.kraAuthority
        return synchronized(lock) { fileSystems[authority] }
            ?: throw FileSystemNotFoundException(authority.toString())
    }

    internal fun removeFileSystem(fileSystem: KraFileSystem) {
        val authority = fileSystem.authority
        synchronized(lock) {
            fileSystems.remove(authority)
            clients.remove(authority)?.logout()
            KraPathIdentCache.clearAuthority(authority)
        }
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val authority = uri.kraAuthority
        val path = uri.decodedPathByteString
            ?: throw IllegalArgumentException("URI must have a path")
        return getOrNewFileSystem(authority).getPath(path)
    }

    internal fun getClient(authority: KraAuthority, authentication: PasswordAuthentication): KraApiClient {
        return synchronized(lock) {
            val client = clients.getOrPut(authority) {
                KraApiClient(authority, authentication)
            }
	    client.updateAuthentication(authentication)
	    return client
        }
    }

    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        val write = options.contains(StandardOpenOption.WRITE)
        // Default to READ if neither READ nor WRITE is specified
        val read = options.contains(StandardOpenOption.READ) || !write
        val create = options.contains(StandardOpenOption.CREATE) ||
                     options.contains(StandardOpenOption.CREATE_NEW)

        if (write && !read && !create) {
            throw IllegalArgumentException("Must specify CREATE when opening for write")
        }

        val authentication = getAuthentication(file)
        val client = getClient(file.fileSystem.authority, authentication)

        // Get progress listener from ThreadLocal (set by copy() method)
        val progressListener = progressListenerThreadLocal.get()

        return KraByteChannel(
            path = file,
            client = client,
            read = read && !write,
            write = write,
            progressListener = progressListener
        )
    }

    @Throws(IOException::class)
    override fun newOutputStream(file: Path, vararg options: OpenOption): OutputStream {
        file as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        val write = options.contains(StandardOpenOption.WRITE)
        val create = options.contains(StandardOpenOption.CREATE) ||
                     options.contains(StandardOpenOption.CREATE_NEW)

        if (!write && !create) {
            throw IllegalArgumentException("Must specify WRITE when opening for output")
        }

        val authentication = getAuthentication(file)
        val client = getClient(file.fileSystem.authority, authentication)

        val fileName = file.fileName?.toString()
            ?: throw IOException("Path must have a filename: $file")
        val parent = file.parent?.getIdentWithClient(client)

        // Delete existing file if present
        try {
            val existingIdent = file.getIdentWithClient(client)
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

        val uploadUrl = createInfo.link ?: throw IOException("No upload URL available for: $file")

        // Return streaming output stream that uploads with progress
        return KraTusOutputStream(file, client, uploadUrl, createInfo.ident)
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        val paths = mutableListOf<Path>()
        val authentication = getAuthentication(directory)
        val client = getClient(directory.fileSystem.authority, authentication)

        try {
            val parentIdent = directory.getIdentWithClient(client)
            val files = client.listFiles(parentIdent)

            // Prefetch idents for performance
            val identPairs = files.map { it.name to it.ident }
            KraPathIdentCache.prefetchIdents(directory, identPairs)

            for (file in files) {
                val childPath = directory.resolve(file.name)

                // Cache the ident
                KraPathIdentCache.putIdent(childPath as KraPath, file.ident)

		// Cache file attributes
                val fileinfo = FileInfo(
                    created = file.created,
                    folder = file.folder,
		    name = file.name,
		    password = file.password,
		    shared = file.shared,
		    size = file.size
                )
		KraFileInfoCache.putInfo(file.ident, childPath.fileSystem.authority, fileinfo)

                paths.add(childPath)
            }
        } catch (e: Exception) {
            throw IOException("Failed to list directory: $directory", e)
        }

        return PathListDirectoryStream(paths, filter)
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        val authentication = getAuthentication(directory)
        val client = getClient(directory.fileSystem.authority, authentication)

        try {
            val name = directory.fileName?.toString()
                ?: throw IllegalArgumentException("Directory must have a name")
            val parent = directory.parent?.getIdentWithClient(client)

            val createInfo = client.createFile(name, isFolder = true, parent = parent)

            // Cache the new directory's ident
            KraPathIdentCache.putIdent(directory, createInfo.ident)

            // Invalidate parent directory cache so the new directory appears
            directory.parent?.let { KraPathIdentCache.invalidateDirectory(it) }

            // Notify watch service
            LocalWatchService.onEntryCreated(directory)
        } catch (e: Exception) {
            throw IOException("Failed to create directory: $directory", e)
        }
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        val authentication = getAuthentication(path)
        val client = getClient(path.fileSystem.authority, authentication)

        try {
            val ident = path.getIdentWithClient(client)
                ?: throw NoSuchFileException(path.toString())
            client.deleteFile(ident)

	    // Remove attributes from cache
	    KraFileInfoCache.removeInfo(ident, path.fileSystem.authority)

            // Remove from cache
            KraPathIdentCache.removeIdent(path)

            // Invalidate parent directory cache so the deleted file disappears
            path.parent?.let { KraPathIdentCache.invalidateDirectory(it) }

            // Notify watch service
            LocalWatchService.onEntryDeleted(path)
        } catch (e: Exception) {
            throw IOException("Failed to delete: $path", e)
        }
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {

        // Parse copy options
        val copyOptions = options.toCopyOptions()

        try {
            // Set progress listener in ThreadLocal so newByteChannel() can access it
            progressListenerThreadLocal.set(copyOptions.progressListener)

            when {
                // KRA → KRA (same or different servers)
                source is KraPath && target is KraPath -> {
                    copyKraToKra(source, target, copyOptions)
                }
                // KRA → Foreign (e.g. KRA → Local)
                source is KraPath -> {
                    val authentication = getAuthentication(source)
                    val client = getClient(source.fileSystem.authority, authentication)
                    KraCopyMove.copyToForeign(source, target, copyOptions, client)
                }
                // Foreign → KRA (e.g. Local → KRA)
                target is KraPath -> {
                    val authentication = getAuthentication(target)
                    val client = getClient(target.fileSystem.authority, authentication)
                    KraCopyMove.copyFromForeign(source, target, copyOptions, client)
                }
                // Neither is KRA - should not happen
                else -> {
                    throw IllegalArgumentException("At least one path must be KraPath")
                }
            }
        } finally {
            // Clear ThreadLocal to prevent memory leaks
            progressListenerThreadLocal.remove()
        }
    }

    /**
     * Copy between KRA paths (same or different servers)
     */
    @Throws(IOException::class)
    private fun copyKraToKra(source: KraPath, target: KraPath, copyOptions: CopyOptions) {
        // Check if both are on same KRA server
        val sameAuthority = source.fileSystem.authority == target.fileSystem.authority

        if (sameAuthority) {
            // Can use server-side operations
            copyOnSameServer(source, target)
        } else {
            // Need to download and re-upload between different servers
            copyBetweenServers(source, target)
        }
    }

    @Throws(IOException::class)
    private fun copyOnSameServer(source: KraPath, target: KraPath) {
        val authentication = getAuthentication(source)
        val client = getClient(source.fileSystem.authority, authentication)

        try {
            val sourceIdent = source.getIdentWithClient(client)
                ?: throw NoSuchFileException(source.toString())

            // Get source file info
            val sourceInfo = client.getFileInfo(sourceIdent)

            if (sourceInfo.folder) {
                // Copy folder recursively
                copyFolderRecursive(source, target, client)
            } else {
                val targetName = target.fileName?.toString()
                    ?: throw IllegalArgumentException("Target must have a name")
                val targetParent = target.parent?.getIdentWithClient(client)

                // Server-side file copy
                val copyInfo = client.copyFile(sourceIdent, targetName, targetParent)

                // Cache target ident
                KraPathIdentCache.putIdent(target, copyInfo.ident)

		// Notify watch service
		LocalWatchService.onEntryCreated(target)
            }
        } catch (e: Exception) {
            throw IOException("Failed to copy: $source to $target", e)
        }
    }

    @Throws(IOException::class)
    private fun copyBetweenServers(source: KraPath, target: KraPath) {
        // Download from source and upload to target
        val sourceAuth = getAuthentication(source)
        val sourceClient = getClient(source.fileSystem.authority, sourceAuth)

        val targetAuth = getAuthentication(target)
        val targetClient = getClient(target.fileSystem.authority, targetAuth)

        try {
            val sourceIdent = source.getIdentWithClient(sourceClient)
                ?: throw NoSuchFileException(source.toString())

            // Get source file info
            val sourceInfo = sourceClient.getFileInfo(sourceIdent)

            if (sourceInfo.folder) {
                throw UnsupportedOperationException("Cross-server folder copy not yet supported")
            }

            // Download from source
            val downloadInfo = sourceClient.getDownloadLink(sourceIdent)
            val inputStream = sourceClient.downloadFile(downloadInfo.link)

            // Create target file
            val targetName = target.fileName?.toString()
                ?: throw IllegalArgumentException("Target must have a name")
            val targetParent = target.parent?.getIdentWithClient(targetClient)

            val createInfo = targetClient.createFile(targetName, false, targetParent)

            // Upload to target
            val uploadInfo = targetClient.getUploadLink(createInfo.ident)
            uploadStream(uploadInfo.link, inputStream)

            // Cache target ident
            KraPathIdentCache.putIdent(target, createInfo.ident)
        } catch (e: Exception) {
            throw IOException("Failed to copy between servers: $source to $target", e)
        }
    }

    @Throws(IOException::class)
    private fun copyFolderRecursive(source: KraPath, target: KraPath, client: KraApiClient) {
        // Create target folder
        val targetName = target.fileName?.toString()
            ?: throw IllegalArgumentException("Target must have a name")
        val targetParent = target.parent?.getIdentWithClient(client)

        val createInfo = client.createFile(targetName, true, targetParent)
        KraPathIdentCache.putIdent(target, createInfo.ident)

        // List source folder contents
        val sourceIdent = source.getIdentWithClient(client)
        val files = client.listFiles(sourceIdent)

        // Copy each item
        for (file in files) {
            val sourcePath = source.resolve(file.name) as KraPath
            val targetPath = target.resolve(file.name) as KraPath

            if (file.folder) {
                copyFolderRecursive(sourcePath, targetPath, client)
            } else {
                copyOnSameServer(sourcePath, targetPath)
            }
        }
    }

    @Throws(IOException::class)
    private fun uploadStream(uploadUrl: String, inputStream: InputStream) {
        try {
            val connection = java.net.URL(uploadUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")

            connection.outputStream.use { output ->
                inputStream.copyTo(output)
                output.flush()
            }

            inputStream.close()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Upload failed with response code: $responseCode")
            }
        } catch (e: Exception) {
            throw IOException("Failed to upload to: $uploadUrl", e)
        }
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as? KraPath ?: throw IllegalArgumentException("Source must be KraPath")
        target as? KraPath ?: throw IllegalArgumentException("Target must be KraPath")

        val sameAuthority = source.fileSystem.authority == target.fileSystem.authority

        if (!sameAuthority) {
            // Cross-server move = copy + delete
            copy(source, target, *options)
            delete(source)
            return
        }

        val authentication = getAuthentication(source)
        val client = getClient(source.fileSystem.authority, authentication)

        try {
            val ident = source.getIdentWithClient(client)
                ?: throw NoSuchFileException(source.toString())
            val newName = target.fileName?.toString()
            val newParent = target.parent?.getIdentWithClient(client)

            client.updateFile(ident, name = newName, parent = newParent)

            // Update cache
            KraPathIdentCache.removeIdent(source)
	    KraFileInfoCache.removeInfo(ident, source.fileSystem.authority)
            KraPathIdentCache.putIdent(target, ident)

            // Notify watch service
            LocalWatchService.onEntryDeleted(source)
            LocalWatchService.onEntryCreated(target)
        } catch (e: Exception) {
            throw IOException("Failed to move: $source to $target", e)
        }
    }

    @Throws(IOException::class)
    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? KraPath ?: return false
        path2 as? KraPath ?: return false
        return path == path2
    }

    @Throws(IOException::class)
    override fun isHidden(path: Path): Boolean = false

    @Throws(IOException::class)
    override fun getFileStore(path: Path): FileStore {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        // Check if file exists by getting its attributes
        try {
            readAttributes(path, BasicFileAttributes::class.java)
        } catch (e: NoSuchFileException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Failed to check access: $path", e)
        }

        // Check access modes
        for (mode in modes) {
            when (mode) {
                AccessMode.READ -> { /* Always readable if authenticated */ }
                AccessMode.WRITE -> { /* Always writable if authenticated */ }
                AccessMode.EXECUTE -> throw AccessDeniedException(path.toString())
            }
        }
    }

    @Throws(IOException::class)
    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        path as? KraPath ?: return null
        if (!type.isAssignableFrom(java8.nio.file.attribute.BasicFileAttributeView::class.java)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return KraFileAttributeView(path) as V
    }

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        path as? KraPath ?: throw IllegalArgumentException("Path must be KraPath")

        if (!type.isAssignableFrom(KraFileAttributes::class.java)) {
            throw UnsupportedOperationException("Attribute type not supported: $type")
        }

        val authentication = getAuthentication(path)
        val client = getClient(path.fileSystem.authority, authentication)

        try {
            val ident = path.getIdentWithClient(client)
                ?: throw NoSuchFileException(path.toString())
	    val fileInfo = KraFileInfoCache.getInfo(ident, path.fileSystem.authority, client)
            @Suppress("UNCHECKED_CAST")
            return KraFileAttributes.from(fileInfo, path) as A
        } catch (e: Exception) {
            throw IOException("Failed to read attributes: $path", e)
        }
    }

    @Throws(IOException::class)
    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        throw UnsupportedOperationException()
    }

    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        // Use shorter interval for KRA to ensure faster updates after file operations
        val effectiveInterval = minOf(intervalMillis, 3000L) // Max 3 seconds
        return WatchServicePathObservable(path, effectiveInterval)
    }

    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        WalkFileTreeSearchable.search(directory, query, intervalMillis, listener)
    }

    private fun URI.requireSameScheme() {
        require(scheme == SCHEME) { "URI scheme must be $SCHEME" }
    }

    private val URI.kraAuthority: KraAuthority
        get() {
            val userInfo = rawUserInfo
                ?: throw IllegalArgumentException("URI must have user info")
            val username = userInfo.substringBefore(':')
            val host = host ?: KraAuthority.DEFAULT_HOST
            val port = if (port != -1) port else KraAuthority.DEFAULT_PORT
            return KraAuthority(host, port, username)
        }

    @Throws(IOException::class)
    internal fun getAuthentication(path: KraPath): PasswordAuthentication {
        // Get authentication from Settings
        val authority = path.fileSystem.authority
        val server = Settings.STORAGES.valueCompat.find { storage ->
            storage is KraServer && storage.authority == authority
        } as? KraServer

        return server?.authentication
            ?: throw IOException("No authentication found for: ${authority}")
    }
}
