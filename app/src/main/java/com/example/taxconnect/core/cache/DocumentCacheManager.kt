package com.example.taxconnect.core.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object DocumentCacheManager {
    
    private const val TAG = "DocumentCacheManager"
    private const val CACHE_DIR_NAME = "documents"
    
    /**
     * Get the cache directory for documents
     */
    private fun getCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }
    
    /**
     * Generate a safe filename from URL
     */
    private fun getFileNameFromUrl(url: String): String {
        return url.hashCode().toString() + "_" + url.substringAfterLast("/").take(50)
    }
    
    /**
     * Check if document is cached
     */
    fun isDocumentCached(context: Context, documentUrl: String): Boolean {
        val fileName = getFileNameFromUrl(documentUrl)
        val file = File(getCacheDir(context), fileName)
        return file.exists() && file.length() > 0
    }
    
    /**
     * Get cached document file
     */
    fun getCachedDocument(context: Context, documentUrl: String): File? {
        val fileName = getFileNameFromUrl(documentUrl)
        val file = File(getCacheDir(context), fileName)
        return if (file.exists() && file.length() > 0) file else null
    }
    
    /**
     * Download and cache document
     */
    suspend fun cacheDocument(context: Context, documentUrl: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUrl(documentUrl)
            val cacheFile = File(getCacheDir(context), fileName)
            
            // Download file
            val url = URL(documentUrl)
            val connection = url.openConnection()
            connection.connect()
            
            val inputStream = connection.getInputStream()
            val outputStream = FileOutputStream(cacheFile)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "Document cached successfully: $fileName")
            Result.success(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error caching document", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete cached document
     */
    fun deleteCachedDocument(context: Context, documentUrl: String): Boolean {
        val fileName = getFileNameFromUrl(documentUrl)
        val file = File(getCacheDir(context), fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    /**
     * Get total cache size in bytes
     */
    fun getCacheSize(context: Context): Long {
        val cacheDir = getCacheDir(context)
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * Get cache size in human-readable format
     */
    fun getCacheSizeFormatted(context: Context): String {
        val bytes = getCacheSize(context)
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Clear all cached documents
     */
    fun clearCache(context: Context): Boolean {
        val cacheDir = getCacheDir(context)
        return try {
            cacheDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            false
        }
    }
    
    /**
     * Get number of cached documents
     */
    fun getCachedDocumentCount(context: Context): Int {
        val cacheDir = getCacheDir(context)
        return cacheDir.listFiles()?.size ?: 0
    }
}
