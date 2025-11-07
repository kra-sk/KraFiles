/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import sk.kra.files.util.Stateful

class AddLanSmbServerViewModel : ViewModel() {
    private val _lanSmbServerListLiveData = LanSmbServerListLiveData()
    val lanSmbServerListLiveData: LiveData<Stateful<List<LanSmbServer>>> = _lanSmbServerListLiveData

    fun reload() {
        _lanSmbServerListLiveData.loadValue()
    }

    override fun onCleared() {
        _lanSmbServerListLiveData.close()
    }
}
