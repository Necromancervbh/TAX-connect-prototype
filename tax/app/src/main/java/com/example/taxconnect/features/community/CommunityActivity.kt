package com.example.taxconnect.features.community

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivityCommunityBinding
import com.example.taxconnect.features.community.PostAdapter
import com.example.taxconnect.data.models.PostModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.core.base.BaseActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import java.util.UUID

class CommunityActivity : BaseActivity<ActivityCommunityBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityCommunityBinding = ActivityCommunityBinding::inflate
    private lateinit var adapter: PostAdapter
    private var currentUser: UserModel? = null

    override fun initViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupRecyclerView()
        fetchCurrentUser()
        // Posts will be loaded in onResume
    }

    override fun setupListeners() {
        binding.fabCreatePost.setOnClickListener { showCreatePostDialog() }
    }

    override fun observeViewModel() {
        // No ViewModel to observe yet
    }

    override fun onResume() {
        super.onResume()
        loadPosts()
    }

    private fun fetchCurrentUser() {
        val uid = FirebaseAuth.getInstance().uid ?: return

        DataRepository.getInstance().fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                currentUser = data
                if (data != null && ::adapter.isInitialized) {
                    adapter.setCurrentUserId(data.uid)
                }
            }

            override fun onError(error: String?) {
                // Handle error
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = PostAdapter(object : PostAdapter.OnPostActionListener {
            override fun onPostClick(post: PostModel) {
                openPostDetail(post)
            }

            override fun onLikeClick(post: PostModel) {
                val user = currentUser ?: return
                val pid = post.id ?: return
                val uid = user.uid ?: return

                DataRepository.getInstance().toggleLike(pid, uid, object : DataRepository.DataCallback<Boolean> {
                    override fun onSuccess(isLiked: Boolean?) {
                        if (isLiked == null) return
                        
                        val likedBy = post.likedBy.toMutableList()
                        
                        if (isLiked) {
                            if (!likedBy.contains(uid)) likedBy.add(uid)
                        } else {
                            likedBy.remove(uid)
                        }
                        post.likedBy = likedBy
                        post.likeCount = likedBy.size
                        adapter.notifyDataSetChanged()
                    }

                    override fun onError(error: String?) {
                        showToast("Failed to update like: $error")
                    }
                })
            }

            override fun onCommentClick(post: PostModel) {
                openPostDetail(post)
            }
        })
        
        binding.rvPosts.layoutManager = LinearLayoutManager(this)
        binding.rvPosts.adapter = adapter
    }

    private fun openPostDetail(post: PostModel) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }

    private fun loadPosts() {
        DataRepository.getInstance().getPosts(object : DataRepository.DataCallback<List<PostModel>> {
            override fun onSuccess(data: List<PostModel>?) {
                if (data != null) {
                    adapter.setPosts(data)
                }
            }

            override fun onError(error: String?) {
                showToast("Failed to load posts")
            }
        })
    }

    private fun showCreatePostDialog() {
        if (currentUser == null) {
            showToast(getString(R.string.loading_user_profile))
            return
        }

        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_post, null)
        builder.setView(view)
        val dialog = builder.create()

        val etPostContent = view.findViewById<TextInputEditText>(R.id.etPostContent)
        val btnPost = view.findViewById<Button>(R.id.btnPost)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        btnPost.setOnClickListener {
            val content = etPostContent.text.toString().trim()
            if (content.isEmpty()) {
                etPostContent.error = getString(R.string.post_content_empty)
                etPostContent.requestFocus()
                return@setOnClickListener
            }

            createPost(content)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun createPost(content: String) {
        val user = currentUser ?: return
        
        val post = PostModel(
            id = UUID.randomUUID().toString(),
            userId = user.uid,
            userName = user.name,
            userRole = user.role,
            content = content,
            timestamp = Date(),
            likeCount = 0,
            commentCount = 0,
            likedBy = ArrayList()
        )

        DataRepository.getInstance().createPost(post, object : DataRepository.DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                showToast(getString(R.string.post_created))
                loadPosts()
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_create_post))
            }
        })
    }
}
