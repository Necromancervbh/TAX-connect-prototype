package com.example.taxconnect.features.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.RequestItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestsAdapter(private val listener: OnRequestActionListener) :
    RecyclerView.Adapter<RequestsAdapter.RequestViewHolder>() {

    private var items: List<RequestItem> = ArrayList()

    interface OnRequestActionListener {
        fun onAccept(item: RequestItem)
        fun onReject(item: RequestItem)
    }

    fun setItems(items: List<RequestItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, listener)
    }

    override fun getItemCount(): Int = items.size

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvCity: TextView = itemView.findViewById(R.id.tvCity)
        private val tvQuery: TextView = itemView.findViewById(R.id.tvQuery)
        private val chipReturning: Chip = itemView.findViewById(R.id.chipReturning)
        private val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)

        fun bind(item: RequestItem, listener: OnRequestActionListener) {
            val user = item.userModel
            chipReturning.visibility = if (item.isReturning) View.VISIBLE else View.GONE
            
            if (user != null) {
                tvName.text = user.name ?: "Unknown User"
                tvCity.text = user.city ?: ""
                tvCity.visibility = if (user.city.isNullOrEmpty()) View.GONE else View.VISIBLE
                
                if (!user.profileImageUrl.isNullOrEmpty()) {
                    ivProfile.setPadding(0, 0, 0, 0)
                    ivProfile.imageTintList = null
                    com.bumptech.glide.Glide.with(itemView.context)
                        .load(user.profileImageUrl)
                        .placeholder(R.drawable.ic_person_material3)
                        .error(R.drawable.ic_person_material3)
                        .circleCrop()
                        .into(ivProfile)
                } else {
                    ivProfile.setPadding(12, 12, 12, 12)
                    ivProfile.setImageResource(R.drawable.ic_person_material3)
                    ivProfile.imageTintList = android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_theme_onSurfaceVariant)
                    )
                }
            } else {
                tvCity.visibility = View.GONE
            }

            when (item.type) {
                RequestItem.TYPE_CONVERSATION -> {
                    val conversation = item.data as ConversationModel
                    if (user == null) {
                        tvName.text = conversation.otherUserName ?: "New Request"
                        
                        if (!conversation.otherUserProfileImage.isNullOrEmpty()) {
                            ivProfile.setPadding(0, 0, 0, 0)
                            ivProfile.imageTintList = null
                            com.bumptech.glide.Glide.with(itemView.context)
                                .load(conversation.otherUserProfileImage)
                                .placeholder(R.drawable.ic_person_material3)
                                .error(R.drawable.ic_person_material3)
                                .circleCrop()
                                .into(ivProfile)
                        } else {
                            ivProfile.setPadding(12, 12, 12, 12)
                            ivProfile.setImageResource(R.drawable.ic_person_material3)
                            ivProfile.imageTintList = android.content.res.ColorStateList.valueOf(
                                androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_theme_onSurfaceVariant)
                            )
                        }
                    }
                    tvQuery.text = conversation.lastMessage ?: "Conversation Request"
                }
                RequestItem.TYPE_BOOKING -> {
                    val booking = item.data as BookingModel
                    if (user == null) {
                        tvName.text = booking.userName ?: "Booking"
                        ivProfile.setPadding(12, 12, 12, 12)
                        ivProfile.setImageResource(R.drawable.ic_calendar_material3)
                        ivProfile.imageTintList = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_theme_onSurfaceVariant)
                        )
                    }
                    
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    val dateStr = sdf.format(Date(booking.appointmentTimestamp))
                    tvQuery.text = "Slot: $dateStr"
                }
            }

            btnAccept.setOnClickListener { listener.onAccept(item) }
            btnReject.setOnClickListener { listener.onReject(item) }
        }
    }
}
