package com.oxoghost.hexapic.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexapic.data.MediaItem
import com.oxoghost.hexapic.data.MediaRepository
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MediaRepository(app)

    private val _mediaItems = MutableLiveData<List<MediaItem>>(emptyList())
    val mediaItems: LiveData<List<MediaItem>> = _mediaItems

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun loadMedia() {
        if (_loading.value == true) return
        _loading.value = true
        viewModelScope.launch {
            _mediaItems.value = repository.loadAllMedia()
            _loading.value = false
        }
    }

    val photoCount: Int get() = _mediaItems.value?.count { !it.isVideo } ?: 0
    val videoCount: Int get() = _mediaItems.value?.count { it.isVideo } ?: 0
}
