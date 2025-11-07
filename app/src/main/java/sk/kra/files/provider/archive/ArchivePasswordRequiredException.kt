/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.provider.archive

import android.content.Context
import java8.nio.file.Path
import sk.kra.files.fileaction.ArchivePasswordDialogActivity
import sk.kra.files.fileaction.ArchivePasswordDialogFragment
import sk.kra.files.provider.common.UserAction
import sk.kra.files.provider.common.UserActionRequiredException
import sk.kra.files.util.createIntent
import sk.kra.files.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ArchivePasswordRequiredException(
    private val file: Path,
    reason: String?
) :
    UserActionRequiredException(file.toString(), null, reason) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        return UserAction(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(
                ArchivePasswordDialogFragment.Args(file) { continuation.resume(it) }
            ), ArchivePasswordDialogFragment.getTitle(context),
            ArchivePasswordDialogFragment.getMessage(file, context)
        )
    }
}
