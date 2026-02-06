package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.model.DocumentModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder> {

    private List<DocumentModel> documents = new ArrayList<>();
    private final OnDocumentClickListener listener;

    public interface OnDocumentClickListener {
        void onDownloadClick(DocumentModel document);
    }

    public DocumentAdapter(OnDocumentClickListener listener) {
        this.listener = listener;
    }

    public void setDocuments(List<DocumentModel> documents) {
        this.documents = documents;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document, parent, false);
        return new DocumentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        DocumentModel document = documents.get(position);
        holder.bind(document, listener);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    static class DocumentViewHolder extends RecyclerView.ViewHolder {
        TextView tvDocName, tvDocDate;
        ImageView ivDocIcon;
        ImageButton btnDownload;

        public DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDocName = itemView.findViewById(R.id.tvDocName);
            tvDocDate = itemView.findViewById(R.id.tvDocDate);
            ivDocIcon = itemView.findViewById(R.id.ivDocIcon);
            btnDownload = itemView.findViewById(R.id.btnDownload);
        }

        public void bind(DocumentModel document, OnDocumentClickListener listener) {
            tvDocName.setText(document.getName());
            
            if (document.getUploadedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                tvDocDate.setText(sdf.format(document.getUploadedAt().toDate()));
            } else {
                tvDocDate.setText("");
            }

            // Set icon based on type if needed, default is ic_work
            
            btnDownload.setOnClickListener(v -> {
                if (listener != null) listener.onDownloadClick(document);
            });
        }
    }
}
