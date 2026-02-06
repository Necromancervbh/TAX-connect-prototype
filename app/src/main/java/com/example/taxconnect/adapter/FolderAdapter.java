package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.google.android.material.chip.Chip;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private List<String> folders;
    private String selectedFolder;
    private final OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(String folder);
    }

    public FolderAdapter(List<String> folders, String selectedFolder, OnFolderClickListener listener) {
        this.folders = folders;
        this.selectedFolder = selectedFolder;
        this.listener = listener;
    }

    public void setFolders(List<String> folders) {
        this.folders = folders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // We inflate the chip directly or a layout containing it
        android.view.View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_chip, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        String folder = folders.get(position);
        holder.bind(folder, folder.equals(selectedFolder), listener);
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        Chip chip;

        public FolderViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            chip = (Chip) itemView;
        }

        public void bind(String folder, boolean isSelected, OnFolderClickListener listener) {
            chip.setText(folder);
            chip.setChecked(isSelected);
            
            chip.setOnClickListener(v -> {
                selectedFolder = folder;
                notifyDataSetChanged(); // Refresh state
                listener.onFolderClick(folder);
            });
        }
    }
}
