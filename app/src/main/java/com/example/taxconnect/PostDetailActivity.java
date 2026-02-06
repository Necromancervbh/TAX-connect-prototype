package com.example.taxconnect;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.adapter.CommentAdapter;
import com.example.taxconnect.model.CommentModel;
import com.example.taxconnect.model.PostModel;
import com.example.taxconnect.model.UserModel;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class PostDetailActivity extends AppCompatActivity {

    private PostModel post;
    private DataRepository repository;
    private CommentAdapter adapter;
    private EditText etComment;
    private UserModel currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        post = (PostModel) getIntent().getSerializableExtra("post");
        if (post == null) {
            finish();
            return;
        }

        repository = DataRepository.getInstance();

        setupUI();
        setupRecyclerView();
        fetchCurrentUser();
        loadComments();
    }

    private void setupUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvAuthorName = findViewById(R.id.tvPostAuthor);
        TextView tvAuthorRole = findViewById(R.id.tvPostRole);
        TextView tvTimestamp = findViewById(R.id.tvPostTimestamp);
        TextView tvContent = findViewById(R.id.tvPostContent);
        TextView tvLikeCount = findViewById(R.id.tvLikeCount);
        TextView tvCommentCount = findViewById(R.id.tvCommentCount);
        etComment = findViewById(R.id.etComment);
        ImageButton btnSendComment = findViewById(R.id.btnSendComment);

        tvAuthorName.setText(post.getUserName());
        tvContent.setText(post.getContent());
        tvLikeCount.setText(String.valueOf(post.getLikeCount()));
        tvCommentCount.setText(String.valueOf(post.getCommentCount()));

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

        btnSendComment.setOnClickListener(v -> postComment());
    }

    private void setupRecyclerView() {
        RecyclerView rvComments = findViewById(R.id.rvComments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentAdapter();
        rvComments.setAdapter(adapter);
    }

    private void fetchCurrentUser() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        
        repository.fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel data) {
                currentUser = data;
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    private void loadComments() {
        repository.getComments(post.getId(), new DataRepository.DataCallback<List<CommentModel>>() {
            @Override
            public void onSuccess(List<CommentModel> data) {
                adapter.setComments(data);
                // Update comment count view
                ((TextView) findViewById(R.id.tvCommentCount)).setText(String.valueOf(data.size()));
            }

            @Override
            public void onError(String error) {
                Toast.makeText(PostDetailActivity.this, "Failed to load comments", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void postComment() {
        String content = etComment.getText().toString().trim();
        if (TextUtils.isEmpty(content)) return;
        if (currentUser == null) {
            Toast.makeText(this, "Loading user profile...", Toast.LENGTH_SHORT).show();
            return;
        }

        String commentId = java.util.UUID.randomUUID().toString();
        CommentModel comment = new CommentModel(
            commentId,
            post.getId(),
            currentUser.getUid(),
            currentUser.getName(),
            currentUser.getRole(),
            content,
            new java.util.Date()
        );

        repository.addComment(post.getId(), comment, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                etComment.setText("");
                loadComments(); // Refresh list
                Toast.makeText(PostDetailActivity.this, "Comment added", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(PostDetailActivity.this, "Failed to add comment: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}