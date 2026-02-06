package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.R;
import com.example.taxconnect.model.PostModel;

import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private List<PostModel> posts = new ArrayList<>();
    private final OnPostClickListener listener;
    private String currentUserId;

    public interface OnPostClickListener {
        void onPostClick(PostModel post);
        void onLikeClick(PostModel post);
        void onCommentClick(PostModel post);
    }

    public PostAdapter(OnPostClickListener listener, String currentUserId) {
        this.listener = listener;
        this.currentUserId = currentUserId;
    }
    
    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
        notifyDataSetChanged();
    }

    public void setPosts(List<PostModel> posts) {
        this.posts = posts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        PostModel post = posts.get(position);
        holder.bind(post, listener, currentUserId);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthorName, tvAuthorRole, tvTimestamp, tvContent, tvLikeCount, tvCommentCount;
        LinearLayout btnLike, btnComment;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            tvAuthorRole = itemView.findViewById(R.id.tvAuthorRole);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
        }

        public void bind(PostModel post, OnPostClickListener listener, String currentUserId) {
            tvAuthorName.setText(post.getUserName());
            tvContent.setText(post.getContent());
            
            if ("CA".equals(post.getUserRole())) {
                tvAuthorRole.setVisibility(View.VISIBLE);
                tvAuthorRole.setText("CA");
            } else {
                tvAuthorRole.setVisibility(View.GONE);
            }

            if (post.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                tvTimestamp.setText(sdf.format(post.getTimestamp()));
            }

            tvLikeCount.setText(String.valueOf(post.getLikeCount()));
            tvCommentCount.setText(String.valueOf(post.getCommentCount()));

            // Update Like Button Appearance
            ImageView ivLike = (ImageView) btnLike.getChildAt(0); // Assuming ImageView is first child
            TextView tvLikeText = (TextView) btnLike.getChildAt(1); // Assuming TextView is second
            
            if (post.getLikedBy() != null && currentUserId != null && post.getLikedBy().contains(currentUserId)) {
                ivLike.setColorFilter(itemView.getContext().getColor(R.color.primary));
                tvLikeText.setTextColor(itemView.getContext().getColor(R.color.primary));
            } else {
                ivLike.setColorFilter(itemView.getContext().getColor(R.color.text_muted));
                tvLikeText.setTextColor(itemView.getContext().getColor(R.color.text_muted));
            }

            btnLike.setOnClickListener(v -> listener.onLikeClick(post));
            btnComment.setOnClickListener(v -> listener.onCommentClick(post));
            itemView.setOnClickListener(v -> listener.onPostClick(post));
        }
    }
}
