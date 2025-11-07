/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
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

class ConfirmReplaceFileDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val file = args.file
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setMessage(getString(R.string.file_replace_message_format, file.name))
            .setPositiveButton(android.R.string.ok) { _, _ -> listener.replaceFile(file) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            ConfirmReplaceFileDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun replaceFile(file: FileItem)
    }
}
