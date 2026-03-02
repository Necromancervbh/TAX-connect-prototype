package com.example.taxconnect.features.community

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.features.community.CommentAdapter
import com.example.taxconnect.databinding.ActivityPostDetailBinding
import com.example.taxconnect.data.models.CommentModel
import com.example.taxconnect.data.models.PostModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.core.base.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PostDetailActivity : BaseActivity<ActivityPostDetailBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityPostDetailBinding = ActivityPostDetailBinding::inflate

    private var post: PostModel? = null
    private lateinit var adapter: CommentAdapter
    private var currentUser: UserModel? = null

    override fun initViews() {
        post = getSerializableExtra(intent, "post", PostModel::class.java)
        if (post == null) {
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        fetchCurrentUser()
        loadComments()
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSendComment.setOnClickListener { postComment() }
    }

    override fun observeViewModel() {
        // No ViewModel to observe yet
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun <T : Serializable> getSerializableExtra(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(key, clazz)
        } else {
            intent.getSerializableExtra(key) as? T
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        val p = post!!
        binding.tvPostAuthor.text = p.userName
        binding.tvPostContent.text = p.content
        binding.tvLikeCount.text = p.likeCount.toString()
        binding.tvCommentCount.text = p.commentCount.toString()

        if ("CA" == p.userRole) {
            binding.tvPostRole.visibility = View.VISIBLE
            binding.tvPostRole.text = getString(R.string.ca_role)
        } else {
            binding.tvPostRole.visibility = View.GONE
        }

        if (p.timestamp != null) {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            binding.tvPostTimestamp.text = sdf.format(p.timestamp!!)
        }
    }

    private fun setupRecyclerView() {
        binding.rvComments.layoutManager = LinearLayoutManager(this)
        adapter = CommentAdapter()
        binding.rvComments.adapter = adapter
    }

    private fun fetchCurrentUser() {
        val uid = FirebaseAuth.getInstance().uid ?: return

        DataRepository.getInstance().fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                currentUser = data
            }

            override fun onError(error: String?) {
                // Handle error
            }
        })
    }

    private fun loadComments() {
        val p = post ?: return
        val postId = p.id ?: return
        DataRepository.getInstance().getComments(postId, object : DataRepository.DataCallback<List<CommentModel>> {
            override fun onSuccess(data: List<CommentModel>?) {
                if (data != null) {
                    adapter.setComments(data)
                    binding.tvCommentCount.text = data.size.toString()
                }
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_load_comments))
            }
        })
    }

    private fun postComment() {
        val content = binding.etComment.text.toString().trim()
        if (TextUtils.isEmpty(content)) {
            binding.etComment.error = getString(R.string.comment_empty_error)
            binding.etComment.requestFocus()
            return
        }
        if (currentUser == null) {
            showToast(getString(R.string.loading_user_profile))
            return
        }

        val p = post ?: return
        val postId = p.id ?: return
        val user = currentUser!!

        val comment = CommentModel(
            id = UUID.randomUUID().toString(),
            postId = postId,
            userId = user.uid,
            userName = user.name,
            userRole = user.role,
            content = content,
            timestamp = Date()
        )

        DataRepository.getInstance().addComment(postId, comment, object : DataRepository.DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                binding.etComment.setText("")
                loadComments() // Refresh list
                showToast(getString(R.string.comment_added))
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_add_comment, error))
            }
        })
    }
}
