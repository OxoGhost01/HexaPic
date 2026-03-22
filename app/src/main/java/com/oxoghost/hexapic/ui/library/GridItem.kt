package com.oxoghost.hexapic.ui.library

import com.oxoghost.hexapic.data.MediaItem

sealed class GridItem {
    data class MonthHeader(val label: String) : GridItem()
    data class YearSeparator(val year: Int) : GridItem()
    data class Photo(val media: MediaItem) : GridItem()
    data class Footer(val photoCount: Int, val videoCount: Int) : GridItem()
}
