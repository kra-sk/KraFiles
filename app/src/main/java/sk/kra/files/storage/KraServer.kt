package sk.kra.files.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import sk.kra.files.R
import sk.kra.files.provider.kra.client.KraAuthority
import sk.kra.files.provider.kra.client.PasswordAuthentication
import sk.kra.files.provider.kra.createKraRootPath
import sk.kra.files.util.createIntent
import sk.kra.files.util.putArgs
import kotlin.random.Random

@Parcelize
class KraServer(
    override val id: Long,
    override val customName: String?,
    val authority: KraAuthority,
    val authentication: PasswordAuthentication
) : Storage() {

    constructor(
        id: Long?,
        customName: String?,
        authority: KraAuthority,
        authentication: PasswordAuthentication
    ) : this(id ?: Random.nextLong(), customName, authority, authentication)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.computer_icon_white_24dp

    override fun getDefaultName(context: Context): String = authority.toString()

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createKraRootPath()

    override fun createEditIntent(): Intent =
        EditKraServerActivity::class.createIntent().putArgs(EditKraServerFragment.Args(this))
}
