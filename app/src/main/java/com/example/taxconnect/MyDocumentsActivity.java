package com.example.taxconnect;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.databinding.ActivityMyDocumentsBinding;
import com.example.taxconnect.services.CloudinaryHelper;
import com.google.firebase.auth.FirebaseAuth;

import com.example.taxconnect.adapter.DocumentAdapter;
import com.example.taxconnect.adapter.FolderAdapter;
import com.example.taxconnect.model.DocumentModel;
import com.example.taxconnect.model.UserModel;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.widget.ArrayAdapter;

public class MyDocumentsActivity extends AppCompatActivity {

    private ActivityMyDocumentsBinding binding;
    private DocumentAdapter adapter;
    private FolderAdapter folderAdapter;
    private List<DocumentModel> allDocuments = new ArrayList<>();
    private String currentFolder = "All";

    private final ActivityResultLauncher<String> pickDocLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadDocument(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyDocumentsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupFolderRecyclerView();
        setupRecyclerView();
        setupListeners();
        checkUserRoleAndCustomizeUI();
        loadDocuments();
    }

    private void setupFolderRecyclerView() {
        List<String> folders = new ArrayList<>();
        folders.add("All");
        folders.add("Personal");
        folders.add("Business");
        folders.add("FY 2023-24");
        folders.add("FY 2024-25");
        folders.add("Others");

        folderAdapter = new FolderAdapter(folders, currentFolder, folder -> {
            currentFolder = folder;
            filterDocuments();
        });
        
        binding.rvFolders.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvFolders.setAdapter(folderAdapter);
    }

    private void filterDocuments() {
        if (currentFolder.equals("All")) {
            adapter.setDocuments(allDocuments);
            binding.tvEmptyState.setVisibility(allDocuments.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            List<DocumentModel> filtered = new ArrayList<>();
            for (DocumentModel doc : allDocuments) {
                if (currentFolder.equals(doc.getCategory()) || 
                   (doc.getCategory() == null && "Uncategorized".equals(currentFolder))) {
                    filtered.add(doc);
                }
            }
            adapter.setDocuments(filtered);
            binding.tvEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void checkUserRoleAndCustomizeUI() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DataRepository.getInstance().fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                if (user != null && !"CA".equals(user.getRole())) {
                    // Customer View
                    binding.toolbarDocs.setTitle("My Documents");
                    binding.tvUploadTitle.setText("Upload Documents");
                    binding.tvUploadDesc.setText("Keep your tax documents and identity proofs organized and secure.");
                    binding.tvListTitle.setText("Uploaded Documents");
                    binding.btnPickDoc.setText("Upload Document");
                }
            }

            @Override
            public void onError(String error) {
                // Default to CA view or existing view on error
            }
        });
    }

    private void setupToolbar() {
        binding.toolbarDocs.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        binding.btnPickDoc.setOnClickListener(v -> pickDocLauncher.launch("*/*"));
    }

    private void setupRecyclerView() {
        adapter = new DocumentAdapter(document -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(document.getUrl()));
                request.setTitle(document.getName());
                request.setDescription("Downloading file...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, document.getName());

                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Download failed: Manager not available", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Download error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        binding.rvMyDocs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMyDocs.setAdapter(adapter);
    }

    private void loadDocuments() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DataRepository.getInstance().getDocuments(uid, new DataRepository.DataCallback<List<DocumentModel>>() {
            @Override
            public void onSuccess(List<DocumentModel> data) {
                allDocuments = data;
                // Retrofit old documents
                for (DocumentModel doc : allDocuments) {
                    if (doc.getCategory() == null) {
                        doc.setCategory("Uncategorized");
                    }
                }
                filterDocuments();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MyDocumentsActivity.this, "Failed to load docs", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadDocument(android.net.Uri uri) {
        binding.uploadProgress.setVisibility(View.VISIBLE);
        binding.btnPickDoc.setEnabled(false);

        // Get file name
        String fileName = getFileName(uri);

        CloudinaryHelper.uploadMedia(this, uri, new CloudinaryHelper.ImageUploadCallback() {
            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> {
                    // Save to Firestore
                    saveDocumentToFirestore(fileName, url);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.uploadProgress.setVisibility(View.GONE);
                    binding.btnPickDoc.setEnabled(true);
                    Toast.makeText(MyDocumentsActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveDocumentToFirestore(String name, String url) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DocumentModel doc = new DocumentModel();
        doc.setId(java.util.UUID.randomUUID().toString());
        doc.setName(name);
        doc.setUrl(url);
        doc.setType(name.endsWith(".pdf") ? "pdf" : "image");
        String category = "Uncategorized";
        if (!currentFolder.equals("All")) {
            category = currentFolder;
        }
        doc.setCategory(category);
        doc.setUploadedAt(Timestamp.now());

        DataRepository.getInstance().saveDocument(uid, doc, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                binding.uploadProgress.setVisibility(View.GONE);
                binding.btnPickDoc.setEnabled(true);
                Toast.makeText(MyDocumentsActivity.this, "Document Uploaded", Toast.LENGTH_SHORT).show();
                loadDocuments(); // Reload list
            }

            @Override
            public void onError(String error) {
                binding.uploadProgress.setVisibility(View.GONE);
                binding.btnPickDoc.setEnabled(true);
                Toast.makeText(MyDocumentsActivity.this, "Save failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getFileName(android.net.Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
