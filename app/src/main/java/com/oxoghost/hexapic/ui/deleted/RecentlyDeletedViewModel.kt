package com.oxoghost.hexapic.ui.deleted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexapic.data.RecentlyDeletedRepository
import com.oxoghost.hexapic.data.db.DeletedItem
import kotlinx.coroutines.launch

class RecentlyDeletedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RecentlyDeletedRepository(app)

    private val _items = MutableLiveData<List<DeletedItem>>(emptyList())
    val items: LiveData<List<DeletedItem>> = _items

    private val _selectionMode = MutableLiveData(false)
    private val _selectedIds   = MutableLiveData<Set<Long>>(emptySet())

    val selectionMode: LiveData<Boolean>   = _selectionMode
    val selectedIds:   LiveData<Set<Long>> = _selectedIds

    init {
        viewModelScope.launch {
            repo.observeAll().collect { _items.value = it }
        }
    }

    fun enterSelectionMode() { _selectionMode.value = true }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val cur = _selectedIds.value ?: emptySet()
        _selectedIds.value = if (id in cur) cur - id else cur + id
    }

    fun selectAll() {
        _selectedIds.value = (_items.value?.map { it.mediaId } ?: emptyList()).toSet()
    }

    fun deselectAll() { _selectedIds.value = emptySet() }

    fun recover(ids: List<Long>) {
        viewModelScope.launch {
            repo.recover(ids)
            exitSelectionMode()
        }
    }

    /** Removes DB records after permanent device deletion is confirmed. */
    fun removeFromDb(ids: List<Long>) {
        viewModelScope.launch {
            repo.removeFromDb(ids)
            exitSelectionMode()
        }
    }

    suspend fun getItemsByIds(ids: List<Long>): List<DeletedItem> =
        repo.getByIds(ids)
}
