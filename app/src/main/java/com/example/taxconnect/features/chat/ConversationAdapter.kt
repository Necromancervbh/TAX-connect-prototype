package com.example.taxconnect.features.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ItemConversationBinding
import com.example.taxconnect.data.models.ConversationModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val listener: OnConversationClickListener?,
    private val currentUserId: String
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    private var conversations: List<ConversationModel> = ArrayList()
    private var blockedUsers: List<String> = ArrayList()

    interface OnConversationClickListener {
        fun onConversationClick(conversation: ConversationModel)
    }

    fun setBlockedUsers(blockedUsers: List<String>) {
        this.blockedUsers = blockedUsers
        notifyDataSetChanged()
    }

    fun setConversations(list: List<ConversationModel>) {
        this.conversations = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bind(conversation)
    }

    override fun getItemCount(): Int {
        return conversations.size
    }

    inner class ConversationViewHolder(private val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ConversationModel) {
            binding.tvUserName.text = conversation.otherUserName ?: "Unknown User"
            binding.tvLastMessage.text = formatLastMessage(conversation.lastMessage)
            binding.tvTime.text = formatTime(conversation.lastMessageTimestamp)

            // Load Profile Image
            if (!conversation.otherUserProfileImage.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(conversation.otherUserProfileImage)
                    .placeholder(R.drawable.ic_person_material3)
                    .error(R.drawable.ic_person_material3)
                    .into(binding.ivProfile)
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_person_material3)
            }

            val workflowState = conversation.workflowState ?: ConversationModel.STATE_DISCUSSION
            var status = getWorkflowLabel(workflowState)
            val statusColor = getWorkflowColor(workflowState)

            var otherUserId = ""
            val participantIds = conversation.participantIds
            if (participantIds != null) {
                for (id in participantIds) {
                    if (id != currentUserId) {
                        otherUserId = id
                        break
                    }
                }
            }

            if (blockedUsers.contains(otherUserId)) {
                status += " (Blocked)"
                binding.tvStatus.setTextColor(Color.RED)
            } else {
                try {
                    val color = androidx.core.content.ContextCompat.getColor(binding.root.context, statusColor)
                    binding.tvStatus.setTextColor(color)
                } catch (e: Exception) {
                    binding.tvStatus.setTextColor(Color.BLACK) // Fallback
                }
            }

            binding.tvStatus.text = status

            binding.root.setOnClickListener {
                listener?.onConversationClick(conversation)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val date = Date(timestamp)
            val now = Date()
            val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            return if (sameDay.format(date) == sameDay.format(now)) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                sdf.format(date)
            } else {
                val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                sdf.format(date)
            }
        }

        private fun formatLastMessage(message: String?): String {
            if (message.isNullOrBlank()) return ""
            val lower = message.lowercase()
            return when {
                lower.startsWith("http") && (lower.contains(".jpg") || lower.contains(".jpeg") ||
                    lower.contains(".png") || lower.contains(".gif") || lower.contains(".webp")) -> "📷 Photo"
                lower.startsWith("http") && (lower.contains(".pdf") || lower.contains(".doc") ||
                    lower.contains(".xls") || lower.contains(".ppt") || lower.contains("file")) -> "📎 Document"
                lower.startsWith("http") && lower.contains("cloudinary") -> "📎 Attachment"
                lower.startsWith("http") -> "🔗 Link"
                else -> message
            }
        }

        private fun getWorkflowLabel(state: String): String {
            return when (state) {
                ConversationModel.STATE_REQUESTED -> "Request pending"
                ConversationModel.STATE_REFUSED -> "Request declined"
                ConversationModel.STATE_PRICE_NEGOTIATION -> "Price negotiation"
                ConversationModel.STATE_PRICE_AGREEMENT -> "Price agreed"
                ConversationModel.STATE_ADVANCE_PAYMENT -> "Awaiting advance"
                ConversationModel.STATE_DOCS_REQUEST -> "Documents requested"
                ConversationModel.STATE_FINAL_PAYMENT -> "Final payment due"
                ConversationModel.STATE_COMPLETED -> "Completed"
                else -> {
                    // Handle raw enum strings like ADVANCE_PAYMENT from old Firestore data
                    if (state.contains("_")) {
                        state.split("_").joinToString(" ") { word ->
                            word.lowercase().replaceFirstChar { it.uppercase() }
                        }
                    } else {
                        "Discussion"
                    }
                }
            }
        }

        private fun getWorkflowColor(state: String): Int {
            return when (state) {
                ConversationModel.STATE_REFUSED -> R.color.slate_500
                ConversationModel.STATE_COMPLETED -> R.color.emerald_600
                ConversationModel.STATE_REQUESTED,
                ConversationModel.STATE_PRICE_NEGOTIATION,
                ConversationModel.STATE_PRICE_AGREEMENT,
                ConversationModel.STATE_ADVANCE_PAYMENT,
                ConversationModel.STATE_FINAL_PAYMENT,
                ConversationModel.STATE_DOCS_REQUEST -> R.color.secondary
                else -> R.color.primary
            }
        }
    }
}
