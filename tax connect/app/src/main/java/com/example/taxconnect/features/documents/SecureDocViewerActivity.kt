package com.example.taxconnect.features.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivitySecureDocViewerBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.view.LayoutInflater
import com.example.taxconnect.core.base.BaseActivity

class SecureDocViewerActivity : BaseActivity<ActivitySecureDocViewerBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivitySecureDocViewerBinding = ActivitySecureDocViewerBinding::inflate

    private var docUrl: String? = null
    private var docType: String? = null
    private var tempFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    override fun initViews() {
        // PREVENT SCREENSHOTS
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        docUrl = intent.getStringExtra("DOC_URL")
        docType = intent.getStringExtra("DOC_TYPE")
        val title = intent.getStringExtra("DOC_TITLE")
        if (title != null) binding.tvTitle.text = title

        if (docUrl == null) {
            showToast(getString(R.string.invalid_document))
            finish()
            return
        }

        loadDocument()
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeViewModel() {
        // No ViewModel used currently
    }

    private fun loadDocument() {
        binding.progressBar.visibility = View.VISIBLE

        if ("image".equals(docType, ignoreCase = true)) {
            binding.rvPdfPages.visibility = View.GONE
            binding.ivDoc.visibility = View.VISIBLE
            Glide.with(this)
                .load(docUrl)
                .into(binding.ivDoc)
            binding.progressBar.visibility = View.GONE
        } else if ("pdf".equals(docType, ignoreCase = true)) {
            binding.ivDoc.visibility = View.GONE
            binding.rvPdfPages.visibility = View.VISIBLE
            downloadAndRenderPdf(docUrl!!)
        } else {
            // Default to image if unknown, or try to load as image
            binding.rvPdfPages.visibility = View.GONE
            binding.ivDoc.visibility = View.VISIBLE
            Glide.with(this)
                .load(docUrl)
                .into(binding.ivDoc)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun downloadAndRenderPdf(urlString: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                val input = connection.inputStream

                tempFile = File(cacheDir, "secure_view_" + System.currentTimeMillis() + ".pdf")
                val output = FileOutputStream(tempFile)

                val buffer = ByteArray(1024)
                var len: Int
                while (input.read(buffer).also { len = it } != -1) {
                    output.write(buffer, 0, len)
                }
                output.close()
                input.close()

                runOnUiThread { renderPdf() }

            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error loading PDF")
                runOnUiThread {
                    showToast("Error loading PDF")
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun renderPdf() {
        try {
            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)

            val adapter = PdfPageAdapter(pdfRenderer!!)
            binding.rvPdfPages.layoutManager = LinearLayoutManager(this)
            binding.rvPdfPages.adapter = adapter

            binding.progressBar.visibility = View.GONE

        } catch (e: IOException) {
            timber.log.Timber.e(e, "Error rendering PDF")
            showToast(getString(R.string.error_rendering_pdf))
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            if (tempFile != null && tempFile!!.exists()) {
                tempFile!!.delete()
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error cleaning up secure document viewer")
        }
    }

    private class PdfPageAdapter(private val renderer: PdfRenderer) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val imageView = ImageView(parent.context)
            imageView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            imageView.adjustViewBounds = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            return PageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = renderer.openPage(position)

            // High quality rendering
            val width = page.width * 2
            val height = page.height * 2

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            (holder.itemView as ImageView).setImageBitmap(bitmap)

            page.close()
        }

        override fun getItemCount(): Int {
            return renderer.pageCount
        }

        class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}
