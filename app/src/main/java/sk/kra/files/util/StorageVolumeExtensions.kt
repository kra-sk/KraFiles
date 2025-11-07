package sk.kra.files.util

import android.os.storage.StorageVolume
import sk.kra.files.compat.directoryCompat

val StorageVolume.isMounted: Boolean
    get() = directoryCompat != null
