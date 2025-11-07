/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.fileproperties.checksum

import android.os.AsyncTask
import java8.nio.file.Path
import sk.kra.files.fileproperties.PathObserverLiveData
import sk.kra.files.provider.common.newInputStream
import sk.kra.files.util.Failure
import sk.kra.files.util.Loading
import sk.kra.files.util.Stateful
import sk.kra.files.util.Success
import sk.kra.files.util.toHexString
import sk.kra.files.util.valueCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class ChecksumInfoLiveData(path: Path) : PathObserverLiveData<Stateful<ChecksumInfo>>(path) {
    private var future: Future<Unit>? = null

    init {
        loadValue()
        observe()
    }

    override fun loadValue() {
        future?.cancel(true)
        value = Loading(value?.value)
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit<Unit> {
            val value = try {
                val messageDigests =
                    ChecksumInfo.Algorithm.entries.associateWith { it.createMessageDigest() }
                path.newInputStream().use { inputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val readSize = inputStream.read(buffer)
                        if (readSize == -1) {
                            break
                        }
                        messageDigests.values.forEach { it.update(buffer, 0, readSize) }
                    }
                }
                val checksumInfo = ChecksumInfo(
                    messageDigests.mapValues { it.value.digest().toHexString() }
                )
                Success(checksumInfo)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }

    override fun close() {
        super.close()

        future?.cancel(true)
    }
}
