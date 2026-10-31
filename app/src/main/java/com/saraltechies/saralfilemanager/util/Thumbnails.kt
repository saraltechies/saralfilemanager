package com.saraltechies.saralfilemanager.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.saraltechies.saralfilemanager.files.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small previews for photos and videos in listings, made on the phone and kept in memory. */
object Thumbnails {

    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun supports(category: FileCategory) = category == FileCategory.IMAGE || category == FileCategory.VIDEO

    suspend fun load(file: File, category: FileCategory, sizePx: Int): ImageBitmap? {
        val key = "${file.path}:${file.lastModified()}:$sizePx"
        cache.get(key)?.let { return it.asImageBitmap() }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { make(file, category, sizePx) }.getOrNull()
        } ?: return null
        cache.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    @Suppress("DEPRECATION")
    private fun make(file: File, category: FileCategory, sizePx: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (category == FileCategory.VIDEO) {
                ThumbnailUtils.createVideoThumbnail(file, Size(sizePx, sizePx), null)
            } else {
                ThumbnailUtils.createImageThumbnail(file, Size(sizePx, sizePx), null)
            }
        } else if (category == FileCategory.VIDEO) {
            ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
        } else {
            decodeSampled(file, sizePx)
        }

    private fun decodeSampled(file: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
