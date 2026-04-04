package com.oxoghost.hexapic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_items")
data class DeletedItem(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val mimeType: String,
    val deletedAt: Long, // epoch millis
)
