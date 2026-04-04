package com.oxoghost.hexapic.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexapic.data.MediaItem
import com.oxoghost.hexapic.data.MediaRepository
import com.oxoghost.hexapic.data.RecentlyDeletedRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository        = MediaRepository(app)
    val deletedRepo               = RecentlyDeletedRepository(app)

    private val _mediaItems = MutableLiveData<List<MediaItem>>(emptyList())
    private val _gridItems  = MutableLiveData<List<GridItem>>(emptyList())
    private val _loading    = MutableLiveData(false)
    private val _deletedIds = MutableLiveData<Set<Long>>(emptySet())

    val gridItems:  LiveData<List<GridItem>> = _gridItems
    val loading:    LiveData<Boolean>        = _loading
    val deletedIds: LiveData<Set<Long>>      = _deletedIds

    // ── Selection state ───────────────────────────────────────────────────────

    private val _selectionMode = MutableLiveData(false)
    private val _selectedIds   = MutableLiveData<Set<Long>>(emptySet())

    val selectionMode: LiveData<Boolean>   = _selectionMode
    val selectedIds:   LiveData<Set<Long>> = _selectedIds

    val allMediaIds: List<Long>
        get() = _mediaItems.value?.map { it.id } ?: emptyList()

    fun enterSelectionMode() {
        _selectionMode.value = true
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value ?: emptySet()
        _selectedIds.value = if (id in current) current - id else current + id
    }

    /** Adds [id] to the selection without removing others — used by two-finger pan. */
    fun addToSelection(id: Long) {
        val current = _selectedIds.value ?: emptySet()
        if (id !in current) _selectedIds.value = current + id
    }

    fun selectAll() {
        _selectedIds.value = (_mediaItems.value?.map { it.id } ?: emptyList()).toSet()
    }

    fun deselectAll() {
        _selectedIds.value = emptySet()
    }

    // ── Media loading ─────────────────────────────────────────────────────────

    val photoCount: Int get() = _mediaItems.value?.count { !it.isVideo } ?: 0
    val videoCount: Int get() = _mediaItems.value?.count {  it.isVideo } ?: 0

    init {
        // Keep grid in sync whenever the set of deleted IDs changes (e.g. after soft-delete
        // or recovery in Recently Deleted).
        viewModelScope.launch {
            deletedRepo.observeDeletedIds().collect { ids ->
                val deleted = ids.toSet()
                _deletedIds.value = deleted
                _mediaItems.value?.let { items ->
                    _gridItems.value = buildGridItems(items.filter { it.id !in deleted })
                }
            }
        }
    }

    fun loadMedia() {
        if (_loading.value == true) return
        _loading.value = true
        viewModelScope.launch {
            val items = repository.loadAllMedia()
            val deletedSnapshot = deletedRepo.observeDeletedIds().first().toSet()
            _mediaItems.value = items
            _deletedIds.value = deletedSnapshot
            _gridItems.value  = buildGridItems(items.filter { it.id !in deletedSnapshot })
            _loading.value    = false
        }
    }

    /** Soft-delete: moves selected items to Recently Deleted, exits selection mode. */
    fun softDelete(items: List<MediaItem>) {
        viewModelScope.launch {
            deletedRepo.softDelete(items)
            exitSelectionMode()
        }
    }

    private fun buildGridItems(items: List<MediaItem>): List<GridItem> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<GridItem>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        var lastYear = -1
        var lastMonthYear = ""

        for (item in items) {
            cal.timeInMillis = item.dateAdded * 1000L
            val year      = cal.get(Calendar.YEAR)
            val monthYear = sdf.format(cal.time)

            if (lastYear != -1 && year != lastYear) {
                result.add(GridItem.YearSeparator(year))
            }
            if (monthYear != lastMonthYear) {
                result.add(GridItem.MonthHeader(monthYear))
                lastMonthYear = monthYear
            }

            result.add(GridItem.Photo(item))
            lastYear = year
        }

        result.add(GridItem.Footer(photoCount = items.count { !it.isVideo },
                                   videoCount  = items.count {  it.isVideo }))
        return result
    }
}
