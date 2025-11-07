/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.fileproperties.audio

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import sk.kra.files.util.Stateful

class FilePropertiesAudioTabViewModel(path: Path) : ViewModel() {
    private val _audioInfoLiveData = AudioInfoLiveData(path)
    val audioInfoLiveData: LiveData<Stateful<AudioInfo>>
        get() = _audioInfoLiveData

    fun reload() {
        _audioInfoLiveData.loadValue()
    }

    override fun onCleared() {
        _audioInfoLiveData.close()
    }
}
