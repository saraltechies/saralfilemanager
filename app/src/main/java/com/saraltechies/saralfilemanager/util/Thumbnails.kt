package com.saraltechies.saralfilemanager.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.saraltechies.saralfilemanager.files.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Previews for photos and videos, made on the phone. From quickest to slowest: memory; Android's own thumbnail for
 * anything in its media index; a copy saved in the app's cache; made from the file and then saved. Only a few are
 * made at a time, and one scrolled past before its turn is dropped, so a long gallery stays smooth.
 */
object Thumbnails {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).coerceAtMost(96L * 1024 * 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val workers = Dispatchers.IO.limitedParallelism(4)
    private const val DISK_DIR = "thumbnails"
    private const val DISK_LIMIT_BYTES = 150L * 1024 * 1024

    fun supports(category: FileCategory) = category == FileCategory.IMAGE || category == FileCategory.VIDEO

    /** A preview already in memory, for showing at once with no flicker. */
    fun cached(path: String, lastModified: Long, sizePx: Int): ImageBitmap? = cache.get(key(path, lastModified, sizePx))?.asImageBitmap()

    suspend fun load(context: Context, file: File, lastModified: Long, category: FileCategory, sizePx: Int, mediaId: Long = 0): ImageBitmap? {
        val size = bucket(sizePx)
        val key = key(file.path, lastModified, sizePx)
        cache.get(key)?.let { return it.asImageBitmap() }
        val bitmap = withContext(workers) {
            ensureActive()
            runCatching { fromIndex(context, mediaId, category, size) ?: fromDisk(context, key) ?: make(file, category, size)?.also { save(context, key, it) } }.getOrNull()
        } ?: return null
        cache.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    /** A photo at a size for looking at, turned the right way up. */
    suspend fun full(file: File, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxPx) {
                        val scale = maxPx.toFloat() / longest
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                decodeSampled(file, maxPx)?.let { upright(file, it) }
            }
        }.getOrNull()?.asImageBitmap()
    }

    /** Keeps the saved previews within their limit, removing the oldest. */
    fun trimDisk(context: Context) {
        val all = File(context.cacheDir, DISK_DIR).listFiles() ?: return
        var total = all.sumOf { it.length() }
        if (total <= DISK_LIMIT_BYTES) return
        for (f in all.sortedBy { it.lastModified() }) {
            total -= f.length()
            f.delete()
            if (total <= DISK_LIMIT_BYTES * 3 / 4) break
        }
    }

    /** Sizes are rounded up to a few steps, so changing the number of columns can reuse what's already made. */
    private fun bucket(sizePx: Int): Int = when {
        sizePx <= 160 -> 160
        sizePx <= 320 -> 320
        else -> 512
    }

    private fun key(path: String, lastModified: Long, sizePx: Int) = path + ":" + lastModified + ":" + bucket(sizePx)

    private fun fromIndex(context: Context, id: Long, category: FileCategory, size: Int): Bitmap? {
        if (id <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val base = if (category == FileCategory.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return runCatching { context.contentResolver.loadThumbnail(ContentUris.withAppendedId(base, id), Size(size, size), null) }.getOrNull()
    }

    private fun diskFile(context: Context, key: String): File {
        val name = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, DISK_DIR).apply { mkdirs() }, name)
    }

    private fun fromDisk(context: Context, key: String): Bitmap? {
        val f = diskFile(context, key)
        return if (f.isFile) BitmapFactory.decodeFile(f.path) else null
    }

    private fun save(context: Context, key: String, bitmap: Bitmap) {
        runCatching {
            diskFile(context, key).outputStream().use { out ->
                bitmap.compress(if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 82, out)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun make(file: File, category: FileCategory, size: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (category == FileCategory.VIDEO) {
                ThumbnailUtils.createVideoThumbnail(file, Size(size, size), null)
            } else {
                ThumbnailUtils.createImageThumbnail(file, Size(size, size), null)
            }
        } else if (category == FileCategory.VIDEO) {
            ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
        } else {
            decodeSampled(file, size)?.let { upright(file, it) }
        }

    private fun decodeSampled(file: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Phones store a turned photo as it was taken, with a note saying which way is up. */
    private fun upright(file: File, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        if (degrees == 0) return bitmap
        val turned = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, turned, true)
    }
}
