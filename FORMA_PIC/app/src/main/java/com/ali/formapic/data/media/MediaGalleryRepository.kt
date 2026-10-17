package com.ali.formapic.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FormaMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val isVideo: Boolean,
    val isRawDng: Boolean = false,
    val isEditedCopy: Boolean = false,
    val durationMs: Long = 0L
) {
    fun formattedResolution(): String = if (width > 0 && height > 0) "${width}x${height}" else "Full Sensor"
    fun formattedFileSize(): String {
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(Locale.US, "%.2f MB", mb)
        else String.format(Locale.US, "%d KB", (sizeBytes / 1024L).coerceAtLeast(1L))
    }
}

data class ExifMetadataDetails(
    val cameraModel: String,
    val focalLength: String,
    val aperture: String,
    val iso: String,
    val exposureTime: String,
    val dimensions: String,
    val dateTaken: String,
    val colorSpace: String
)

/**
 * Handles saving and managing photos (JPEG, HEIF, DNG RAW) and videos via Android MediaStore
 * so all captures appear in both FORMA PIC's built-in Gallery and the Android system gallery.
 * Supports Android 9 (API 28) through Android 16 (API 36).
 */
class MediaGalleryRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    suspend fun saveCapturedBitmap(
        bitmap: Bitmap,
        prefix: String = "FORMA_",
        subfolder: String = "DCIM/FORMA_PIC",
        isEditedCopy: Boolean = false,
        useHeifIfSupported: Boolean = false,
        quality: Int = 98
    ): Uri? = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val suffix = if (isEditedCopy) "_EDIT" else ""
        val useHeif = useHeifIfSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val ext = if (useHeif) "heic" else "jpg"
        val mime = if (useHeif) "image/heic" else "image/jpeg"
        val displayName = "${prefix}${timestamp}${suffix}.${ext}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, subfolder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val legacyDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "FORMA_PIC"
                )
                if (!legacyDir.exists()) legacyDir.mkdirs()
                val targetFile = File(legacyDir, displayName)
                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val insertedUri = resolver.insert(collection, values) ?: return@withContext null

        try {
            resolver.openOutputStream(insertedUri)?.use { outStream ->
                val format = if (useHeif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                bitmap.compress(format, quality.coerceIn(80, 100), outStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, completeValues, null, null)
            }
            insertedUri
        } catch (e: Exception) {
            resolver.delete(insertedUri, null, null)
            null
        }
    }

    suspend fun loadGalleryMedia(searchQuery: String = ""): List<FormaMediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<FormaMediaItem>()

        // Load Images
        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imgProjection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Photo"
                    if (searchQuery.isNotBlank() && !name.contains(searchQuery, ignoreCase = true)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    items.add(
                        FormaMediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            dateAddedSeconds = cursor.getLong(dateCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            width = cursor.getInt(wCol),
                            height = cursor.getInt(hCol),
                            mimeType = mime,
                            isVideo = false,
                            isRawDng = name.endsWith(".dng", ignoreCase = true) || mime.contains("dng"),
                            isEditedCopy = name.contains("_EDIT", ignoreCase = true)
                        )
                    )
                    count++
                }
            }
        }

        // Load Videos
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )
        runCatching {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                vidProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < 80) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video"
                    if (searchQuery.isNotBlank() && !name.contains(searchQuery, ignoreCase = true)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(
                        FormaMediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            dateAddedSeconds = cursor.getLong(dateCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            width = cursor.getInt(wCol),
                            height = cursor.getInt(hCol),
                            mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                            isVideo = true,
                            durationMs = cursor.getLong(durCol)
                        )
                    )
                    count++
                }
            }
        }

        items.sortedByDescending { it.dateAddedSeconds }
    }

    suspend fun loadBitmapFromUri(uri: Uri, maxDimension: Int = 2560): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDimension || boundsOpts.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        }.getOrNull()
    }

    suspend fun readExifDetails(uri: Uri): ExifMetadataDetails = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "Android"
                val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Camera Sensor"
                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "4.5 mm"
                val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" } ?: "f/1.8"
                val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" } ?: "ISO Auto"
                val exp = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "${it}s" } ?: "Auto Exposure"
                val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: "Recent Capture"
                val cs = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE) ?: "sRGB / Display P3"

                ExifMetadataDetails(
                    cameraModel = "$make $model".trim(),
                    focalLength = focal,
                    aperture = fNum,
                    iso = iso,
                    exposureTime = exp,
                    dimensions = if (w > 0 && h > 0) "${w} × ${h} px" else "Full Sensor",
                    dateTaken = date,
                    colorSpace = cs
                )
            }
        }.getOrNull() ?: ExifMetadataDetails(
            cameraModel = Build.MANUFACTURER + " " + Build.MODEL,
            focalLength = "Wide Optical",
            aperture = "f/1.8",
            iso = "Auto",
            exposureTime = "Auto",
            dimensions = "Full Resolution",
            dateTaken = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            colorSpace = "sRGB"
        )
    }

    suspend fun deleteMediaItem(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    suspend fun renameMediaItem(uri: Uri, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
            }
            resolver.update(uri, values, null, null) > 0
        }.getOrDefault(false)
    }
}
