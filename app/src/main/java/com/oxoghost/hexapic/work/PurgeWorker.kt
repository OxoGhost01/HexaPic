package com.oxoghost.hexapic.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oxoghost.hexapic.data.RecentlyDeletedRepository

/**
 * Runs daily. Permanently deletes items that have been in Recently Deleted for 30+ days.
 * On API 30+ uses MediaStore.createDeleteRequest semantics via ContentResolver.delete();
 * on older APIs attempts a direct delete. Items are removed from the DB regardless of
 * whether the device-level delete succeeds, so they stop appearing in Recently Deleted.
 */
class PurgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = RecentlyDeletedRepository(applicationContext)
        val expired = repo.getExpired()

        for (item in expired) {
            // ContentResolver.delete() may throw RecoverableSecurityException on API 30+
            // for media the app doesn't own — catch and skip gracefully.
            runCatching {
                applicationContext.contentResolver.delete(Uri.parse(item.uri), null, null)
            }
        }

        repo.purgeExpired()
        return Result.success()
    }
}
