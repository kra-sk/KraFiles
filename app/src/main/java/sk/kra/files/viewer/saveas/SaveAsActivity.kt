/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.viewer.saveas

import android.os.Bundle
import android.os.Environment
import java8.nio.file.Path
import java8.nio.file.Paths
import sk.kra.files.R
import sk.kra.files.app.AppActivity
import sk.kra.files.file.MimeType
import sk.kra.files.file.asMimeTypeOrNull
import sk.kra.files.filejob.FileJobService
import sk.kra.files.filelist.FileListActivity
import sk.kra.files.util.saveAsPaths
import sk.kra.files.util.showToast

class SaveAsActivity : AppActivity() {
    private val createFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract(), ::onCreateFileResult)

    private val openDirectoryLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract(), ::onOpenDirectoryResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val paths = intent.saveAsPaths
        if (paths.isEmpty()) {
            showToast(R.string.save_as_error)
            finish()
            return
        }
        val initialPath =
            Paths.get(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
            )
        if (paths.size == 1) {
            val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
            val title = paths.first().fileName.toString()
            createFileLauncher.launch(Triple(mimeType, title, initialPath))
        } else {
            openDirectoryLauncher.launch(initialPath)
        }
    }

    private fun onCreateFileResult(result: Path?) {
        if (result == null) {
            finish()
            return
        }
        FileJobService.save(intent.saveAsPaths.first(), result, this)
        finish()
    }

    private fun onOpenDirectoryResult(result: Path?) {
        if (result == null) {
            finish()
            return
        }
        FileJobService.copy(intent.saveAsPaths, result, this)
        finish()
    }
}
