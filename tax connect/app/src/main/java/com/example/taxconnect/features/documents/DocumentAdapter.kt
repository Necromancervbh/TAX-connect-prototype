package com.example.taxconnect.features.documents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.DocumentModel
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.taxconnect.core.cache.DocumentCacheManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

class DocumentAdapter(private val listener: OnDocumentClickListener?) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    private var documents: List<DocumentModel> = ArrayList()

    interface OnDocumentClickListener {
        fun onDownloadClick(document: DocumentModel)
        fun onViewClick(document: DocumentModel)
        fun onCacheStatusChanged()
    }

    fun setDocuments(documents: List<DocumentModel>) {
        this.documents = documents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val document = documents[position]
        holder.bind(document, listener)
    }

    override fun getItemCount(): Int {
        return documents.size
    }

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDocName: TextView = itemView.findViewById(R.id.tvDocName)
        private val tvDocDate: TextView = itemView.findViewById(R.id.tvDocDate)
        private val ivDocIcon: ImageView = itemView.findViewById(R.id.ivDocIcon)
        private val btnDownload: MaterialButton = itemView.findViewById(R.id.btnDownload)
        private val chipOfflineStatus: Chip = itemView.findViewById(R.id.chipOfflineStatus)

        fun bind(document: DocumentModel, listener: OnDocumentClickListener?) {
            tvDocName.text = document.name
            
            if (document.uploadedAt != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                tvDocDate.text = sdf.format(document.uploadedAt!!.toDate())
            } else {
                tvDocDate.text = ""
            }

            // Set icon based on type
            if (document.name != null) {
                val name = document.name!!.lowercase()
                if (name.endsWith(".pdf")) {
                    ivDocIcon.setImageResource(R.drawable.ic_work_material3) // Should ideally be a PDF icon
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    ivDocIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
            
            // Check cache status
            val context = itemView.context
            val isCached = document.url?.let { DocumentCacheManager.isDocumentCached(context, it) } ?: false
            
            if (isCached) {
                chipOfflineStatus.visibility = View.VISIBLE
                btnDownload.setIconResource(R.drawable.ic_cloud_done)
                btnDownload.iconTint = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.emerald_700)
                )
            } else {
                chipOfflineStatus.visibility = View.GONE
                btnDownload.setIconResource(R.drawable.ic_cloud_download)
                btnDownload.iconTint = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.primary)
                )
            }
            
            itemView.setOnClickListener {
                listener?.onViewClick(document)
            }
            
            btnDownload.setOnClickListener {
                listener?.onDownloadClick(document)
            }
        }
    }
}
