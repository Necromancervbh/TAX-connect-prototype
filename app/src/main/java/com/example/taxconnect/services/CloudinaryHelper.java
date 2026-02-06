package com.example.taxconnect.services;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CloudinaryHelper {
    private static final String TAG = "CloudinaryHelper";
    // Replace with your actual Cloudinary credentials
    private static final String CLOUD_NAME = com.example.taxconnect.BuildConfig.CLOUDINARY_CLOUD_NAME;
    private static final String UPLOAD_PRESET = com.example.taxconnect.BuildConfig.CLOUDINARY_UPLOAD_PRESET;

    public static void init(Context context) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", CLOUD_NAME);
            config.put("secure", true);
            MediaManager.init(context, config);
        } catch (IllegalStateException e) {
            // Already initialized
            Log.w(TAG, "Cloudinary already initialized");
        }
    }

    public interface ImageUploadCallback {
        void onSuccess(String url);
        void onError(String error);
    }

    public static void uploadMedia(Context context, Uri uri, ImageUploadCallback callback) {
        // Copy to cache to ensure access and valid file path
        String filePath = copyUriToCache(context, uri);
        
        if (filePath == null) {
            callback.onError("Failed to process file for upload.");
            return;
        }

        MediaManager.get().upload(filePath)
                .unsigned(UPLOAD_PRESET)
                .option("resource_type", "auto") // Handle both image and video
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Optional: Update progress
                    }

                    @Override
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        Log.d(TAG, "Upload success: " + url);
                        
                        // Clean up temp file
                        new File(filePath).delete();
                        
                        if (callback != null) {
                            callback.onSuccess(url);
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Upload error: " + error.getDescription());
                        
                        // Clean up temp file
                        new File(filePath).delete();
                        
                        if (callback != null) {
                            callback.onError(error.getDescription());
                        }
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Handle reschedule
                    }
                })
                .dispatch();
    }
    
    private static String copyUriToCache(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            // Try to determine extension
            String extension = ".tmp";
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (ext != null) extension = "." + ext;
            }

            File tempFile = File.createTempFile("upload_", extension, context.getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            outputStream.close();
            inputStream.close();
            
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error copying file to cache: " + e.getMessage());
            return null;
        }
    }

    public static void uploadImage(String filePath, ImageUploadCallback callback) {
        MediaManager.get().upload(filePath)
                .unsigned(UPLOAD_PRESET)
                .option("resource_type", "auto")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        if (callback != null) callback.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (callback != null) callback.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                })
                .dispatch();
    }
}
