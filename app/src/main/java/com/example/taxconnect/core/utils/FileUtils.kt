package com.example.taxconnect.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.taxconnect.core.common.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getFileType(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return when {
            Constants.FileExtensions.IMAGES.contains(extension) -> Constants.FileTypes.IMAGE
            Constants.FileExtensions.VIDEOS.contains(extension) -> Constants.FileTypes.VIDEO
            Constants.FileExtensions.DOCUMENTS.contains(extension) -> Constants.FileTypes.DOCUMENT
            else -> Constants.FileTypes.OTHER
        }
    }

    fun getFileExtension(uri: Uri): String? {
        return context.contentResolver.getType(uri)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } ?: uri.path?.substringAfterLast(".", "")
    }

    fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    fun compressImage(
        uri: Uri,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024,
        quality: Int = 80
    ): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val resizedBitmap = resizeBitmap(originalBitmap, maxWidth, maxHeight)
            val rotatedBitmap = rotateBitmapIfNeeded(resizedBitmap, uri)

            val compressedFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
            FileOutputStream(compressedFile).use { outputStream ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }

            // Clean up bitmaps
            if (originalBitmap != resizedBitmap) resizedBitmap.recycle()
            if (resizedBitmap != rotatedBitmap) rotatedBitmap.recycle()

            compressedFile
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error compressing image")
            null
        }
    }

    fun createTempFile(prefix: String, extension: String): File {
        return File.createTempFile(prefix, ".$extension", context.cacheDir)
    }

    fun copyFileToCache(uri: Uri, fileName: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val extension = fileName.substringAfterLast(".", "")
            val tempFile = createTempFile("temp_", extension)
            
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            tempFile
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error copying file to cache")
            null
        }
    }

    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCacheFiles() {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error deleting cache files")
        }
    }

    fun generateUniqueFileName(originalName: String): String {
        val extension = originalName.substringAfterLast(".", "")
        val baseName = originalName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        
        return "${baseName}_${timestamp}_$uniqueId.$extension"
    }

    fun isValidFileSize(uri: Uri, maxSizeInMB: Int = 10): Boolean {
        val maxSizeInBytes = maxSizeInMB * 1024 * 1024
        return getFileSize(uri) <= maxSizeInBytes
    }

    fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return Constants.FileExtensions.IMAGES.contains(extension)
    }

    fun isVideoFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return Constants.FileExtensions.VIDEOS.contains(extension)
    }

    fun isDocumentFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return Constants.FileExtensions.DOCUMENTS.contains(extension)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val scaleFactor = when {
            width > maxWidth || height > maxHeight -> {
                val widthRatio = maxWidth.toFloat() / width
                val heightRatio = maxHeight.toFloat() / height
                minOf(widthRatio, heightRatio)
            }
            else -> 1f
        }

        return if (scaleFactor < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scaleFactor).toInt(),
                (height * scaleFactor).toInt(),
                true
            )
        } else {
            bitmap
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            if (rotationDegrees == 0) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            bitmap
        }
    }
}