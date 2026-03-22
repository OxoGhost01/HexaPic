package com.oxoghost.hexapic.ui.detail

import com.oxoghost.hexapic.data.MediaItem

/** Process-scoped store for passing the photo list into DetailActivity. */
object DetailDataStore {
    var photos: List<MediaItem> = emptyList()
    var startPosition: Int = 0
}
