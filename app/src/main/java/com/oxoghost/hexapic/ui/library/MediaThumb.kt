package com.oxoghost.hexapic.ui.library

import android.net.Uri

/** Wrapper passed to Coil so the MediaThumbnailFetcher is used instead of a full JPEG decode. */
data class MediaThumb(val uri: Uri, val isVideo: Boolean)
