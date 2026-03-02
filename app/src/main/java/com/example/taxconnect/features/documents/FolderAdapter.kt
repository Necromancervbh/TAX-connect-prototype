package com.example.taxconnect.features.documents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.google.android.material.chip.Chip

class FolderAdapter(
    private var folders: List<String>,
    private var selectedFolder: String,
    private val listener: OnFolderClickListener
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    interface OnFolderClickListener {
        fun onFolderClick(folder: String)
    }

    fun setFolders(folders: List<String>) {
        this.folders = folders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        // We inflate the chip directly or a layout containing it
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_chip, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder, folder == selectedFolder, listener)
    }

    override fun getItemCount(): Int {
        return folders.size
    }

    inner class FolderViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val chip: Chip = itemView as Chip

        fun bind(folder: String, isSelected: Boolean, listener: OnFolderClickListener) {
            chip.text = folder
            chip.isChecked = isSelected
            
            chip.setOnClickListener {
                selectedFolder = folder
                notifyDataSetChanged() // Refresh state
                listener.onFolderClick(folder)
            }
        }
    }
}
