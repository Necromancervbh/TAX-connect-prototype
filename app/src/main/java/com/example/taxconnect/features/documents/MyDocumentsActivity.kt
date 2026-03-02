package com.example.taxconnect.features.documents

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.features.documents.DocumentAdapter
import com.example.taxconnect.features.documents.FolderAdapter
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DataRepository.DataCallback
import com.example.taxconnect.databinding.ActivityMyDocumentsBinding
import com.example.taxconnect.data.models.DocumentModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.services.CloudinaryHelper
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import com.example.taxconnect.core.cache.DocumentCacheManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class MyDocumentsActivity : BaseActivity<ActivityMyDocumentsBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityMyDocumentsBinding = ActivityMyDocumentsBinding::inflate
    private lateinit var adapter: DocumentAdapter
    private lateinit var folderAdapter: FolderAdapter
    private var allDocuments: List<DocumentModel> = ArrayList()
    private var currentFolder = "All"

    private val pickDocLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadDocument(uri)
        }
    }

    override fun initViews() {
        setupToolbar()
        setupFolderRecyclerView()
        setupRecyclerView()
        checkUserRoleAndCustomizeUI()
        loadDocuments()
    }

    override fun observeViewModel() {
        // No ViewModel to observe
    }

    private var selectedUploadCategory: String? = null

    override fun setupListeners() {
        binding.btnPickDoc.setOnClickListener {
            val bottomSheet = UploadDocumentBottomSheet { category ->
                selectedUploadCategory = category
                pickDocLauncher.launch("*/*")
            }
            bottomSheet.show(supportFragmentManager, UploadDocumentBottomSheet.TAG)
        }
    }

    private fun setupFolderRecyclerView() {
        val folders = listOf("All", "Personal", "Business", "FY 2023-24", "FY 2024-25", "Others")

        folderAdapter = FolderAdapter(folders, currentFolder, object : FolderAdapter.OnFolderClickListener {
            override fun onFolderClick(folder: String) {
                currentFolder = folder
                filterDocuments()
            }
        })

        binding.rvFolders.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFolders.adapter = folderAdapter
    }

    private fun filterDocuments() {
        val filtered = if (currentFolder == "All") {
            allDocuments
        } else {
            allDocuments.filter { doc ->
                currentFolder == doc.category || (doc.category == null && "Uncategorized" == currentFolder)
            }
        }
        adapter.setDocuments(filtered)
        
        if (filtered.isEmpty()) {
            binding.layoutEmptyState.root.visibility = View.VISIBLE
            binding.layoutEmptyState.tvEmptyTitle.text = "No Documents"
            binding.layoutEmptyState.tvEmptyDescription.text = "You haven't uploaded any documents in this folder yet."
            binding.layoutEmptyState.ivEmptyIcon.setImageResource(R.drawable.ic_document)
            binding.layoutEmptyState.btnEmptyAction.text = "Upload Document"
            binding.layoutEmptyState.btnEmptyAction.setOnClickListener { binding.btnPickDoc.performClick() }
        } else {
            binding.layoutEmptyState.root.visibility = View.GONE
        }
    }

    private fun checkUserRoleAndCustomizeUI() {
        val uid = FirebaseAuth.getInstance().uid ?: return

        DataRepository.getInstance().fetchUser(uid, object : DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                if (data != null && "CA" != data.role) {
                    // Customer View
                    binding.toolbarDocs.title = getString(R.string.my_documents)
                    binding.tvUploadTitle.text = getString(R.string.upload_documents)
                    binding.tvUploadDesc.text = getString(R.string.upload_documents_desc)
                    binding.tvListTitle.text = getString(R.string.uploaded_documents)
                    binding.btnPickDoc.text = getString(R.string.upload_documents)
                }
            }

            override fun onError(error: String?) {
                // Default to CA view or existing view on error
            }
        })
    }

    private fun setupToolbar() {
        binding.toolbarDocs.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(object : DocumentAdapter.OnDocumentClickListener {
            override fun onDownloadClick(document: DocumentModel) {
                handleDocumentDownload(document)
            }

            override fun onViewClick(document: DocumentModel) {
                openDocument(document)
            }

            override fun onCacheStatusChanged() {
                // Refresh the list to update cache indicators
                adapter.notifyDataSetChanged()
            }
        })
        binding.rvMyDocs.layoutManager = LinearLayoutManager(this)
        binding.rvMyDocs.adapter = adapter
    }

    private fun loadDocuments() {
        val uid = FirebaseAuth.getInstance().uid ?: return

        binding.shimmerViewContainer.root.visibility = View.VISIBLE
        binding.shimmerViewContainer.root.startShimmer()
        binding.layoutEmptyState.root.visibility = View.GONE
        binding.rvMyDocs.visibility = View.GONE
        binding.layoutErrorState.root.visibility = View.GONE

        DataRepository.getInstance().getDocuments(uid, object : DataCallback<List<DocumentModel>> {
            override fun onSuccess(data: List<DocumentModel>?) {
                binding.shimmerViewContainer.root.stopShimmer()
                binding.shimmerViewContainer.root.visibility = View.GONE
                
                if (data != null) {
                    allDocuments = data
                    // Retrofit old documents
                    for (doc in allDocuments) {
                        if (doc.category == null) {
                            doc.category = getString(R.string.uncategorized)
                        }
                    }
                    filterDocuments()
                }
            }

            override fun onError(error: String?) {
                binding.shimmerViewContainer.root.stopShimmer()
                binding.shimmerViewContainer.root.visibility = View.GONE
                
                if (allDocuments.isEmpty()) {
                     binding.rvFolders.visibility = View.GONE
                     binding.rvMyDocs.visibility = View.GONE
                     binding.layoutEmptyState.root.visibility = View.GONE
                     
                     val errorBinding = binding.layoutErrorState
                     errorBinding.root.visibility = View.VISIBLE
                     errorBinding.tvErrorTitle.text = "Error Loading Documents"
                     errorBinding.tvErrorDescription.text = error ?: "Unknown error"
                     errorBinding.btnRetry.setOnClickListener {
                         errorBinding.root.visibility = View.GONE
                         loadDocuments()
                     }
                } else {
                    showToast(error ?: getString(R.string.failed_to_load_docs))
                }
            }
        })
    }

    private fun uploadDocument(uri: Uri) {
        binding.uploadProgress.visibility = View.VISIBLE
        binding.btnPickDoc.isEnabled = false

        val fileName = getFileName(uri)

        CloudinaryHelper.uploadMedia(this, uri, object : CloudinaryHelper.ImageUploadCallback {
            override fun onSuccess(data: String?) {
                runOnUiThread {
                    saveDocumentToFirestore(fileName, data ?: "")
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    binding.uploadProgress.visibility = View.GONE
                    binding.btnPickDoc.isEnabled = true
                    showToast(getString(R.string.upload_failed, error))
                }
            }
        })
    }

    private fun saveDocumentToFirestore(name: String?, url: String) {
        val uid = FirebaseAuth.getInstance().uid ?: return

        val doc = DocumentModel()
        doc.id = UUID.randomUUID().toString()
        doc.name = name
        doc.url = url
        doc.type = if (name?.endsWith(".pdf", ignoreCase = true) == true) "pdf" else "image"
        doc.category = selectedUploadCategory ?: (if (currentFolder == "All") "Uncategorized" else currentFolder)
        doc.uploadedAt = Timestamp.now()

        DataRepository.getInstance().saveDocument(uid, doc, object : DataCallback<Boolean> {
            override fun onSuccess(data: Boolean?) {
                binding.uploadProgress.visibility = View.GONE
                binding.btnPickDoc.isEnabled = true
                showToast(getString(R.string.document_uploaded))
                loadDocuments()
            }

            override fun onError(error: String?) {
                binding.uploadProgress.visibility = View.GONE
                binding.btnPickDoc.isEnabled = true
                showToast(getString(R.string.save_failed, error))
            }
        })
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }


    private fun handleDocumentDownload(document: DocumentModel) {
        val url = document.url ?: return
        
        // Check if already cached
        if (DocumentCacheManager.isDocumentCached(this, url)) {
            Snackbar.make(binding.root, getString(R.string.available_offline), Snackbar.LENGTH_SHORT).show()
            return
        }
        
        // Download and cache
        binding.uploadProgress.visibility = View.VISIBLE
        Snackbar.make(binding.root, getString(R.string.downloading), Snackbar.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            val result = DocumentCacheManager.cacheDocument(this@MyDocumentsActivity, url)
            
            withContext(Dispatchers.Main) {
                binding.uploadProgress.visibility = View.GONE
                
                if (result.isSuccess) {
                    Snackbar.make(binding.root, getString(R.string.download_complete), Snackbar.LENGTH_LONG).show()
                    adapter.notifyDataSetChanged() // Refresh to show offline indicator
                } else {
                    Snackbar.make(binding.root, getString(R.string.download_failed), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openDocument(document: DocumentModel) {
        val url = document.url ?: return
        
        // Check if cached, use cached version
        val cachedFile = DocumentCacheManager.getCachedDocument(this, url)
        val docUrl = if (cachedFile != null) {
            Uri.fromFile(cachedFile).toString()
        } else {
            url
        }
        
        val intent = Intent(this@MyDocumentsActivity, SecureDocViewerActivity::class.java)
        intent.putExtra("DOC_URL", docUrl)
        intent.putExtra("DOC_TITLE", document.name)
        var type = "image"
        if (document.name?.lowercase()?.endsWith(".pdf") == true) {
            type = "pdf"
        }
        intent.putExtra("DOC_TYPE", type)
        startActivity(intent)
    }
}
