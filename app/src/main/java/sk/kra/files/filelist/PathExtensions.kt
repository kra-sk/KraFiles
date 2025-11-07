/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.filelist

import java8.nio.file.Path
import sk.kra.files.file.MimeType
import sk.kra.files.file.isSupportedArchive
import sk.kra.files.provider.archive.archiveFile
import sk.kra.files.provider.archive.isArchivePath
import sk.kra.files.provider.document.isDocumentPath
import sk.kra.files.provider.document.resolver.DocumentResolver
import sk.kra.files.provider.linux.isLinuxPath

val Path.name: String
    get() = fileName?.toString() ?: if (isArchivePath) archiveFile.fileName.toString() else "/"

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
