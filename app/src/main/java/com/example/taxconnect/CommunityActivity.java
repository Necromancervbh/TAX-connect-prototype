package com.example.taxconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.adapter.PostAdapter;
import com.example.taxconnect.model.PostModel;
import com.example.taxconnect.model.UserModel;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.UUID;

public class CommunityActivity extends AppCompatActivity {

    private RecyclerView rvPosts;
    private PostAdapter adapter;
    private ExtendedFloatingActionButton fabCreatePost;
    private DataRepository repository;
    private UserModel currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        repository = DataRepository.getInstance();

        rvPosts = findViewById(R.id.rvPosts);
        fabCreatePost = findViewById(R.id.fabCreatePost);
        
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        setupRecyclerView();
        fetchCurrentUser();
        // Posts will be loaded in onResume
        
        fabCreatePost.setOnClickListener(v -> showCreatePostDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPosts();
    }

    private void fetchCurrentUser() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        
        repository.fetchUser(uid, new DataRepository.DataCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel data) {
                currentUser = data;
                if (adapter != null) {
                    adapter.setCurrentUserId(currentUser.getUid());
                }
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new PostAdapter(new PostAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(PostModel post) {
                openPostDetail(post);
            }

            @Override
            public void onLikeClick(PostModel post) {
                if (currentUser == null) return;
                
                repository.toggleLike(post.getId(), currentUser.getUid(), new DataRepository.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isLiked) {
                        // Refresh specific item or whole list
                        // For simplicity, updating local model and notifying adapter
                        // But since others might like, reloading is safer but heavier.
                        // Let's update locally first for responsiveness.
                        List<String> likedBy = post.getLikedBy();
                        if (likedBy == null) likedBy = new java.util.ArrayList<>();
                        
                        if (isLiked) {
                            if (!likedBy.contains(currentUser.getUid())) likedBy.add(currentUser.getUid());
                        } else {
                            likedBy.remove(currentUser.getUid());
                        }
                        post.setLikedBy(likedBy);
                        post.setLikeCount(likedBy.size());
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(CommunityActivity.this, "Failed to update like: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCommentClick(PostModel post) {
                openPostDetail(post);
            }
        }, null);
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        rvPosts.setAdapter(adapter);
    }

    private void openPostDetail(PostModel post) {
        android.content.Intent intent = new android.content.Intent(this, PostDetailActivity.class);
        intent.putExtra("post", post);
        startActivity(intent);
    }

    private void loadPosts() {
        repository.getPosts(new DataRepository.DataCallback<List<PostModel>>() {
            @Override
            public void onSuccess(List<PostModel> data) {
                adapter.setPosts(data);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CommunityActivity.this, "Failed to load posts", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreatePostDialog() {
        if (currentUser == null) {
            Toast.makeText(this, "Please wait, loading user profile...", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_post, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // The ID in xml is etPostContent, correcting here
        TextInputEditText etPostContent = view.findViewById(R.id.etPostContent);
        
        Button btnPost = view.findViewById(R.id.btnPost);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        btnPost.setOnClickListener(v -> {
            String content = etPostContent.getText().toString().trim();
            if (content.isEmpty()) return;

            createPost(content);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void createPost(String content) {
        PostModel post = new PostModel();
        post.setId(UUID.randomUUID().toString());
        post.setUserId(currentUser.getUid());
        post.setUserName(currentUser.getName());
        post.setUserRole(currentUser.getRole());
        post.setContent(content);
        post.setTimestamp(new java.util.Date());
        post.setLikeCount(0);
        post.setCommentCount(0);

        repository.createPost(post, new DataRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(CommunityActivity.this, "Post Created!", Toast.LENGTH_SHORT).show();
                loadPosts();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CommunityActivity.this, "Failed to create post", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
