package com.example.taxconnect.data.services

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.taxconnect.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object CloudinaryHelper {
    private const val TAG = "CloudinaryHelper"
    // Replace with your actual Cloudinary credentials
    private const val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
    private const val UPLOAD_PRESET = BuildConfig.CLOUDINARY_UPLOAD_PRESET

    fun init(context: Context) {
        try {
            val config: MutableMap<String, Any> = HashMap()
            config["cloud_name"] = CLOUD_NAME
            config["secure"] = true
            MediaManager.init(context, config)
        } catch (e: IllegalStateException) {
            // Already initialized
            Log.w(TAG, "Cloudinary already initialized")
        }
    }

    interface ImageUploadCallback {
        fun onSuccess(url: String?)
        fun onError(error: String?)
    }

    fun uploadMedia(context: Context, uri: Uri, callback: ImageUploadCallback?) {
        // Copy to cache to ensure access and valid file path
        val filePath = copyUriToCache(context, uri)

        if (filePath == null) {
            callback?.onError("Failed to process file for upload.")
            return
        }

        MediaManager.get().upload(filePath)
            .unsigned(UPLOAD_PRESET)
            .option("resource_type", "auto") // Handle both image and video
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    Log.d(TAG, "Upload started: $requestId")
                }

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    // Optional: Update progress
                }

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val url = resultData["secure_url"] as String?
                    Log.d(TAG, "Upload success: $url")

                    // Clean up temp file
                    File(filePath).delete()

                    callback?.onSuccess(url ?: "")
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    Log.e(TAG, "Upload error: " + error.description)

                    // Clean up temp file
                    File(filePath).delete()

                    callback?.onError(error.description)
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    // Handle reschedule
                }
            })
            .dispatch()
    }

    fun uploadFile(filePath: String, callback: ImageUploadCallback?) {
        MediaManager.get().upload(filePath)
            .unsigned(UPLOAD_PRESET)
            .option("resource_type", "auto")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    callback?.onSuccess(resultData["secure_url"] as String?)
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    callback?.onError(error.description)
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    private fun copyUriToCache(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) return null

                // Try to determine extension
                var extension = ".tmp"
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType != null) {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (ext != null) extension = ".$ext"
                }

                val tempFile = File.createTempFile("upload_", extension, context.cacheDir)
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                return tempFile.absolutePath
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying file to cache: " + e.message)
            return null
        }
    }

    fun uploadImage(filePath: String, callback: ImageUploadCallback?) {
        MediaManager.get().upload(filePath)
            .unsigned(UPLOAD_PRESET)
            .option("resource_type", "auto")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    Log.d(TAG, "Upload started: $requestId")
                }

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                }

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val url = resultData["secure_url"] as String?
                    callback?.onSuccess(url ?: "")
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    callback?.onError(error.description)
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                }
            })
            .dispatch()
    }
}
