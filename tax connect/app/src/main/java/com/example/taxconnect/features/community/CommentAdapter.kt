package com.example.taxconnect.features.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.CommentModel
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private var comments: List<CommentModel> = ArrayList()

    fun setComments(comments: List<CommentModel>) {
        this.comments = comments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.bind(comment)
    }

    override fun getItemCount(): Int {
        return comments.size
    }

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAuthorName: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        private val tvAuthorRole: TextView = itemView.findViewById(R.id.tvCommentRole)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvCommentTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvCommentContent)

        fun bind(comment: CommentModel) {
            tvAuthorName.text = comment.userName
            tvContent.text = comment.content

            if ("CA" == comment.userRole) {
                tvAuthorRole.visibility = View.VISIBLE
                tvAuthorRole.text = "CA"
            } else {
                tvAuthorRole.visibility = View.GONE
            }

            val ts = comment.timestamp
            if (ts != null) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                tvTimestamp.text = sdf.format(ts)
            }
        }
    }
}
