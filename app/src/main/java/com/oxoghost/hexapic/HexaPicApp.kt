package com.oxoghost.hexapic

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import com.oxoghost.hexapic.ui.library.MediaThumbnailFetcher
import com.oxoghost.hexapic.work.PurgeWorker
import java.util.concurrent.TimeUnit

class HexaPicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(MediaThumbnailFetcher.Factory()) }
                .build()
        )
        schedulePurgeWorker()
    }

    private fun schedulePurgeWorker() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "purge_recently_deleted",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
        )
    }
}
