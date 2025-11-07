/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.app

import androidx.core.content.edit
import sk.kra.files.BuildConfig

private const val KEY_VERSION_CODE = "key_version_code"

private const val VERSION_CODE_BELOW_1_0_0 = 1
private const val VERSION_CODE_LATEST = BuildConfig.VERSION_CODE

private var lastVersionCode: Int
    get() {
        if (defaultSharedPreferences.all.isEmpty()) {
            // This is a new install.
            lastVersionCode = VERSION_CODE_LATEST
            return VERSION_CODE_LATEST
        }
        return defaultSharedPreferences.getInt(KEY_VERSION_CODE, VERSION_CODE_BELOW_1_0_0)
    }
    set(value) {
        defaultSharedPreferences.edit { putInt(KEY_VERSION_CODE, value) }
    }

fun upgradeApp() {
    upgradeAppFrom(lastVersionCode)
    lastVersionCode = VERSION_CODE_LATEST
}

private fun upgradeAppFrom(lastVersionCode: Int) {
    // Continue with new `if`s on lastVersionCode instead of `else if`.
}
