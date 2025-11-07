/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.util

import android.net.Uri
import sk.kra.files.app.contentResolver

fun Uri.takePersistablePermission(modeFlags: Int): Boolean =
    try {
        contentResolver.takePersistableUriPermission(this, modeFlags)
        true
    } catch (e: SecurityException) {
        e.printStackTrace()
        false
    }

fun Uri.releasePersistablePermission(modeFlags: Int): Boolean =
    try {
        contentResolver.releasePersistableUriPermission(this, modeFlags)
        true
    } catch (e: SecurityException) {
        e.printStackTrace()
        false
    }
