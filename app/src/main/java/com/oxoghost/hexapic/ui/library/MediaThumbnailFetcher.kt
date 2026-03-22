package com.oxoghost.hexapic.ui.library

import android.content.ContentUris
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.provider.MediaStore
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Coil Fetcher that loads thumbnails via the MediaStore thumbnail API instead of decoding the
 * full JPEG. On API 29+ this uses ContentResolver.loadThumbnail() which hits the system thumbnail
 * cache. On older APIs it falls back to the legacy MediaStore.*.Thumbnails helpers.
 */
class MediaThumbnailFetcher(
    private val data: MediaThumb,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val cr = options.context.contentResolver
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val w = (options.size.width  as? Dimension.Pixels)?.px ?: 512
            val h = (options.size.height as? Dimension.Pixels)?.px ?: 512
            cr.loadThumbnail(data.uri, android.util.Size(w, h), null)
        } else {
            val id = ContentUris.parseId(data.uri)
            @Suppress("DEPRECATION")
            if (data.isVideo) {
                MediaStore.Video.Thumbnails.getThumbnail(
                    cr, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(
                    cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                )
            }
        } ?: throw IOException("Null thumbnail for ${data.uri}")

        DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader) =
            MediaThumbnailFetcher(data, options)
    }
}
