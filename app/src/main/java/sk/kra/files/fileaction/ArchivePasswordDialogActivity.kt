/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.fileaction

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import sk.kra.files.app.AppActivity
import sk.kra.files.util.args
import sk.kra.files.util.putArgs

class ArchivePasswordDialogActivity : AppActivity() {
    private val args by args<ArchivePasswordDialogFragment.Args>()

    private lateinit var fragment: ArchivePasswordDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = ArchivePasswordDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, ArchivePasswordDialogFragment::class.java.name)
            }
        } else {
            fragment = supportFragmentManager.findFragmentByTag(
                ArchivePasswordDialogFragment::class.java.name
            ) as ArchivePasswordDialogFragment
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            fragment.onFinish()
        }
    }
}
