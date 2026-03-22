package com.oxoghost.hexapic

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.oxoghost.hexapic.ui.library.MediaThumbnailFetcher

class HexaPicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(MediaThumbnailFetcher.Factory()) }
                .build()
        )
    }
}
