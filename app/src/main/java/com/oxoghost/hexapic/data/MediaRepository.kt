package com.oxoghost.hexapic.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        items += queryImages()
        items += queryVideos()
        // Sort by date added descending (newest first / at bottom when reversed for display)
        items.sortByDescending { it.dateAdded }
        items
    }

    private fun queryImages(): List<MediaItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val hasFavoriteCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.MIME_TYPE)
            if (hasFavoriteCol) add(MediaStore.MediaColumns.IS_FAVORITE)
        }.toTypedArray()
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val favCol = if (hasFavoriteCol) cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items += MediaItem(
                    id = id,
                    uri = uri,
                    dateAdded = cursor.getLong(dateCol),
                    mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                    isFavorite = favCol >= 0 && cursor.getInt(favCol) == 1,
                )
            }
        }
        return items
    }

    private fun queryVideos(): List<MediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val hasFavoriteCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DURATION)
            if (hasFavoriteCol) add(MediaStore.MediaColumns.IS_FAVORITE)
        }.toTypedArray()
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val favCol = if (hasFavoriteCol) cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items += MediaItem(
                    id = id,
                    uri = uri,
                    dateAdded = cursor.getLong(dateCol),
                    mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                    duration = cursor.getLong(durCol),
                    isFavorite = favCol >= 0 && cursor.getInt(favCol) == 1,
                )
            }
        }
        return items
    }
}
