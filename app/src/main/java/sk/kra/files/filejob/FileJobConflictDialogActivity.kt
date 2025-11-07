/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.filejob

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import sk.kra.files.app.AppActivity
import sk.kra.files.util.args
import sk.kra.files.util.putArgs

class FileJobConflictDialogActivity : AppActivity() {
    private val args by args<FileJobConflictDialogFragment.Args>()

    private lateinit var fragment: FileJobConflictDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = FileJobConflictDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, FileJobConflictDialogFragment::class.java.name)
            }
        } else {
            fragment = supportFragmentManager.findFragmentByTag(
                FileJobConflictDialogFragment::class.java.name
            ) as FileJobConflictDialogFragment
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            fragment.onFinish()
        }
    }
}
