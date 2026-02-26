package com.example.taxconnect.features.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.PostModel
import java.text.SimpleDateFormat
import java.util.*

class PostAdapter(private val listener: OnPostActionListener) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private var posts: List<PostModel> = ArrayList()
    private var currentUserId: String? = null

    interface OnPostActionListener {
        fun onPostClick(post: PostModel)
        fun onLikeClick(post: PostModel)
        fun onCommentClick(post: PostModel)
    }

    fun setPosts(posts: List<PostModel>) {
        this.posts = posts
        notifyDataSetChanged()
    }

    fun setCurrentUserId(userId: String?) {
        this.currentUserId = userId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.bind(post, listener)
    }

    override fun getItemCount(): Int = posts.size

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAuthorName: TextView = itemView.findViewById(R.id.tvAuthorName)
        private val tvAuthorRole: TextView = itemView.findViewById(R.id.tvAuthorRole)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        private val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        private val btnLike: View = itemView.findViewById(R.id.btnLike)
        private val btnComment: View = itemView.findViewById(R.id.btnComment)

        fun bind(post: PostModel, listener: OnPostActionListener) {
            tvAuthorName.text = post.userName ?: "Anonymous"
            tvContent.text = post.content
            tvLikeCount.text = post.likeCount.toString()
            tvCommentCount.text = post.commentCount.toString()

            itemView.setOnClickListener { listener.onPostClick(post) }

            if ("CA" == post.userRole) {
                tvAuthorRole.visibility = View.VISIBLE
                tvAuthorRole.text = "CA"
            } else {
                tvAuthorRole.visibility = View.GONE
            }

            if (post.timestamp != null) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                tvTimestamp.text = sdf.format(post.timestamp!!)
            } else {
                tvTimestamp.text = ""
            }

            btnLike.setOnClickListener { listener.onLikeClick(post) }
            btnComment.setOnClickListener { listener.onCommentClick(post) }
        }
    }
}
