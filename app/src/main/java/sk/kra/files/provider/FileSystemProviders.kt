/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.provider

import java8.nio.file.Files
import java8.nio.file.ProviderNotFoundException
import java8.nio.file.spi.FileSystemProvider
import sk.kra.files.provider.archive.ArchiveFileSystemProvider
import sk.kra.files.provider.common.AndroidFileTypeDetector
import sk.kra.files.provider.content.ContentFileSystemProvider
import sk.kra.files.provider.document.DocumentFileSystemProvider
import sk.kra.files.provider.ftp.FtpFileSystemProvider
import sk.kra.files.provider.ftp.FtpesFileSystemProvider
import sk.kra.files.provider.ftp.FtpsFileSystemProvider
import sk.kra.files.provider.linux.LinuxFileSystemProvider
import sk.kra.files.provider.root.isRunningAsRoot
import sk.kra.files.provider.kra.KraFileSystemProvider
import sk.kra.files.provider.sftp.SftpFileSystemProvider
import sk.kra.files.provider.smb.SmbFileSystemProvider
import sk.kra.files.provider.webdav.WebDavFileSystemProvider
import sk.kra.files.provider.webdav.WebDavsFileSystemProvider

object FileSystemProviders {
    /**
     * If set, WatchService implementations will skip processing any event data and simply send an
     * overflow event to all the registered keys upon successful read from the inotify fd. This can
     * help reducing the JNI and GC overhead when large amount of inotify events are generated.
     * Simply sending an overflow event to all the keys is okay because we use only one key per
     * service for WatchServicePathObservable.
     */
    @Volatile
    var overflowWatchEvents = false

    fun install() {
        FileSystemProvider.installDefaultProvider(LinuxFileSystemProvider)
        FileSystemProvider.installProvider(ArchiveFileSystemProvider)
        if (!isRunningAsRoot) {
            FileSystemProvider.installProvider(ContentFileSystemProvider)
            FileSystemProvider.installProvider(DocumentFileSystemProvider)
            FileSystemProvider.installProvider(FtpFileSystemProvider)
            FileSystemProvider.installProvider(FtpsFileSystemProvider)
            FileSystemProvider.installProvider(FtpesFileSystemProvider)
            FileSystemProvider.installProvider(KraFileSystemProvider)
            FileSystemProvider.installProvider(SftpFileSystemProvider)
            FileSystemProvider.installProvider(SmbFileSystemProvider)
            FileSystemProvider.installProvider(WebDavFileSystemProvider)
            FileSystemProvider.installProvider(WebDavsFileSystemProvider)
        }
        Files.installFileTypeDetector(AndroidFileTypeDetector)
    }

    operator fun get(scheme: String): FileSystemProvider {
        for (provider in FileSystemProvider.installedProviders()) {
            if (provider.scheme.equals(scheme, ignoreCase = true)) {
                return provider
            }
        }
        throw ProviderNotFoundException(scheme)
    }
}
