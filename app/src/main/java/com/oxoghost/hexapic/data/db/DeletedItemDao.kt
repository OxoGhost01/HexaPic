package com.oxoghost.hexapic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DeletedItem>)

    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<DeletedItem>>

    @Query("SELECT mediaId FROM deleted_items")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT * FROM deleted_items WHERE mediaId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DeletedItem>

    @Query("SELECT * FROM deleted_items WHERE deletedAt < :cutoff")
    suspend fun getExpired(cutoff: Long): List<DeletedItem>

    @Query("DELETE FROM deleted_items WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM deleted_items WHERE deletedAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}
