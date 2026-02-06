package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.model.CommentModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<CommentModel> comments = new ArrayList<>();

    public void setComments(List<CommentModel> comments) {
        this.comments = comments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        CommentModel comment = comments.get(position);
        holder.bind(comment);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthorName, tvAuthorRole, tvTimestamp, tvContent;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthorName = itemView.findViewById(R.id.tvCommentAuthor);
            tvAuthorRole = itemView.findViewById(R.id.tvCommentRole);
            tvTimestamp = itemView.findViewById(R.id.tvCommentTimestamp);
            tvContent = itemView.findViewById(R.id.tvCommentContent);
        }

        public void bind(CommentModel comment) {
            tvAuthorName.setText(comment.getUserName());
            tvContent.setText(comment.getContent());

            if ("CA".equals(comment.getUserRole())) {
                tvAuthorRole.setVisibility(View.VISIBLE);
                tvAuthorRole.setText("CA");
            } else {
                tvAuthorRole.setVisibility(View.GONE);
            }

            if (comment.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                tvTimestamp.setText(sdf.format(comment.getTimestamp()));
            }
        }
    }
}