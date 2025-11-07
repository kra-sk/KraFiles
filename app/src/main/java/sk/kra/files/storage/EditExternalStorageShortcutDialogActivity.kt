/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import sk.kra.files.app.AppActivity
import sk.kra.files.util.args
import sk.kra.files.util.putArgs

class EditExternalStorageShortcutDialogActivity : AppActivity() {
    private val args by args<EditExternalStorageShortcutDialogFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditExternalStorageShortcutDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, EditExternalStorageShortcutDialogFragment::class.java.name)
            }
        }
    }
}
