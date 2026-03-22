package com.oxoghost.hexapic.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,        // epoch seconds
    val mimeType: String,
    val duration: Long = 0L,    // milliseconds; 0 for images
    val isFavorite: Boolean = false,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
