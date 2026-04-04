package com.oxoghost.hexapic.data

import android.content.Context
import com.oxoghost.hexapic.data.db.DeletedItem
import com.oxoghost.hexapic.data.db.HexaPicDatabase
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class RecentlyDeletedRepository(context: Context) {

    private val dao = HexaPicDatabase.get(context).deletedItemDao()

    fun observeAll(): Flow<List<DeletedItem>> = dao.observeAll()

    fun observeDeletedIds(): Flow<List<Long>> = dao.observeIds()

    suspend fun softDelete(items: List<MediaItem>) {
        val now = System.currentTimeMillis()
        dao.insertAll(items.map { DeletedItem(it.id, it.uri.toString(), it.mimeType, now) })
    }

    /** Recover: removes items from the deleted set so they reappear in the library. */
    suspend fun recover(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun getByIds(ids: List<Long>): List<DeletedItem> = dao.getByIds(ids)

    /** Items that have been in Recently Deleted for more than 30 days. */
    suspend fun getExpired(): List<DeletedItem> =
        dao.getExpired(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))

    /** Removes expired DB records (called by PurgeWorker). */
    suspend fun purgeExpired() =
        dao.deleteExpired(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))

    /** Removes specific items from DB (after permanent device deletion succeeds). */
    suspend fun removeFromDb(ids: List<Long>) = dao.deleteByIds(ids)
}
