/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.provider.common

import android.os.ParcelFileDescriptor
import java8.nio.channels.FileChannel
import java8.nio.channels.FileChannels
import sk.kra.files.compat.NioUtilsCompat
import sk.kra.files.provider.linux.syscall.Syscall
import sk.kra.files.provider.linux.syscall.SyscallException
import java.io.Closeable
import java.io.FileDescriptor
import java.io.IOException
import kotlin.reflect.KClass

fun KClass<FileChannel>.open(fd: FileDescriptor, flags: Int): FileChannel {
    val closeable = Closeable {
        try {
            Syscall.close(fd)
        } catch (e: SyscallException) {
            throw IOException(e)
        }
    }
    return FileChannels.from(NioUtilsCompat.newFileChannel(closeable, fd, flags))
}

fun KClass<FileChannel>.open(pfd: ParcelFileDescriptor, mode: String): FileChannel =
    FileChannels.from(
        NioUtilsCompat.newFileChannel(
            pfd, pfd.fileDescriptor,
            ParcelFileDescriptor::class.modeToFlags(ParcelFileDescriptor.parseMode(mode))
        )
    )
