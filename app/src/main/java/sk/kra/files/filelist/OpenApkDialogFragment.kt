/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import sk.kra.files.R
import sk.kra.files.file.FileItem
import sk.kra.files.util.ParcelableArgs
import sk.kra.files.util.args
import sk.kra.files.util.putArgs
import sk.kra.files.util.show

class OpenApkDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setMessage(R.string.file_open_apk_message)
            .setPositiveButton(R.string.install) { _, _ -> listener.installApk(args.file) }
            // While semantically incorrect, this places the two most expected actions side by side.
            .setNegativeButton(R.string.view) { _, _ -> listener.viewApk(args.file) }
            .setNeutralButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            OpenApkDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun installApk(file: FileItem)
        fun viewApk(file: FileItem)
    }
}
