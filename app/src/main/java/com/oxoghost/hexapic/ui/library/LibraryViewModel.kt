package com.oxoghost.hexapic.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexapic.data.MediaItem
import com.oxoghost.hexapic.data.MediaRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MediaRepository(app)

    private val _mediaItems = MutableLiveData<List<MediaItem>>(emptyList())
    private val _gridItems = MutableLiveData<List<GridItem>>(emptyList())
    private val _loading = MutableLiveData(false)

    val gridItems: LiveData<List<GridItem>> = _gridItems
    val loading: LiveData<Boolean> = _loading

    val photoCount: Int get() = _mediaItems.value?.count { !it.isVideo } ?: 0
    val videoCount: Int get() = _mediaItems.value?.count { it.isVideo } ?: 0

    fun loadMedia() {
        if (_loading.value == true) return
        _loading.value = true
        viewModelScope.launch {
            val items = repository.loadAllMedia()
            _mediaItems.value = items
            _gridItems.value = buildGridItems(items)
            _loading.value = false
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
            val year = cal.get(Calendar.YEAR)
            val monthYear = sdf.format(cal.time)

            // Year separator when transitioning to an older year
            if (lastYear != -1 && year != lastYear) {
                result.add(GridItem.YearSeparator(year))
            }
            // Month section header
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
