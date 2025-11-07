/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import sk.kra.files.R
import sk.kra.files.provider.sftp.client.Authentication
import sk.kra.files.provider.sftp.client.Authority
import sk.kra.files.provider.sftp.createSftpRootPath
import sk.kra.files.util.createIntent
import sk.kra.files.util.putArgs
import kotlin.random.Random

@Parcelize
class SftpServer(
    override val id: Long,
    override val customName: String?,
    val authority: Authority,
    val authentication: Authentication,
    val relativePath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        authority: Authority,
        authentication: Authentication,
        relativePath: String
    ) : this(id ?: Random.nextLong(), customName, authority, authentication, relativePath)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.computer_icon_white_24dp

    override fun getDefaultName(context: Context): String =
        if (relativePath.isNotEmpty()) "$authority/$relativePath" else authority.toString()

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createSftpRootPath().resolve(relativePath)

    override fun createEditIntent(): Intent =
        EditSftpServerActivity::class.createIntent().putArgs(EditSftpServerFragment.Args(this))
}
