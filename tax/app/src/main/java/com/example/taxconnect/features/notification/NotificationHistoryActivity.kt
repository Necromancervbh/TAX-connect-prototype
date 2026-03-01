package com.example.taxconnect.features.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.NotificationModel
import com.example.taxconnect.data.repositories.NotificationRepository
import com.example.taxconnect.databinding.ActivityNotificationHistoryBinding
import com.example.taxconnect.core.base.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import java.text.SimpleDateFormat
import java.util.*

class NotificationHistoryActivity : BaseActivity<ActivityNotificationHistoryBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityNotificationHistoryBinding = ActivityNotificationHistoryBinding::inflate
    private lateinit var repository: NotificationRepository

    override fun initViews() {
        repository = NotificationRepository(this)
        setupToolbar()
        setupRecyclerView()
    }

    override fun setupListeners() {
        // No additional listeners
    }

    override fun observeViewModel() {
        // No ViewModel
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        repository.getNotifications { notifications ->
            if (notifications.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvNotifications.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvNotifications.visibility = View.VISIBLE
                
                val groupedList = groupNotifications(notifications)
                binding.rvNotifications.adapter = NotificationAdapter(groupedList)
            }
        }
    }

    private fun groupNotifications(notifications: List<NotificationModel>): List<NotificationListItem> {
        val todayList = mutableListOf<NotificationModel>()
        val yesterdayList = mutableListOf<NotificationModel>()
        val olderList = mutableListOf<NotificationModel>()
        
        val calendar = Calendar.getInstance()
        val todayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
        
        // Sort descending
        notifications.sortedByDescending { it.timestamp }.forEach {
            when {
                it.timestamp >= todayStart -> todayList.add(it)
                it.timestamp >= yesterdayStart -> yesterdayList.add(it)
                else -> olderList.add(it)
            }
        }
        
        val result = mutableListOf<NotificationListItem>()
        if (todayList.isNotEmpty()) {
            result.add(NotificationListItem.HeaderItem(getString(R.string.notification_header_today)))
            result.addAll(todayList.map { NotificationListItem.NotifItem(it) })
        }
        if (yesterdayList.isNotEmpty()) {
            result.add(NotificationListItem.HeaderItem(getString(R.string.notification_header_yesterday)))
            result.addAll(yesterdayList.map { NotificationListItem.NotifItem(it) })
        }
        if (olderList.isNotEmpty()) {
            result.add(NotificationListItem.HeaderItem(getString(R.string.notification_header_older)))
            result.addAll(olderList.map { NotificationListItem.NotifItem(it) })
        }
        
        return result
    }

    sealed class NotificationListItem {
        data class HeaderItem(val title: String) : NotificationListItem()
        data class NotifItem(val notification: NotificationModel) : NotificationListItem()
    }

    inner class NotificationAdapter(private val items: List<NotificationListItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_HEADER = 0
        private val VIEW_TYPE_ITEM = 1

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is NotificationListItem.HeaderItem -> VIEW_TYPE_HEADER
                is NotificationListItem.NotifItem -> VIEW_TYPE_ITEM
            }
        }

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeaderTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
        }

        inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvBody: TextView = view.findViewById(R.id.tvBody)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification, parent, false)
                NotificationViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            
            if (holder is HeaderViewHolder && item is NotificationListItem.HeaderItem) {
                holder.tvHeaderTitle.text = item.title
            } else if (holder is NotificationViewHolder && item is NotificationListItem.NotifItem) {
                val notification = item.notification
                
                // Smart display logic
                var displayTitle = notification.title
                var displayBody = notification.body
                
                if (notification.data != null) {
                    when (notification.type) {
                        "message" -> {
                            val senderName = notification.data["senderName"]
                            if (!senderName.isNullOrEmpty()) {
                                displayTitle = senderName
                            }
                        }
                        "call" -> {
                            val callerName = notification.data["callerName"]
                            if (!callerName.isNullOrEmpty()) {
                                displayTitle = getString(R.string.notification_call_from, callerName)
                            }
                        }
                    }
                }

                holder.tvTitle.text = displayTitle ?: getString(R.string.notification_default_title)
                holder.tvBody.text = displayBody ?: ""
                
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                holder.tvTime.text = sdf.format(Date(notification.timestamp))

                val iconRes = when (notification.type) {
                    "message" -> R.drawable.ic_chat_bubble_material3
                    "call" -> R.drawable.ic_video_call_material3
                    "request" -> R.drawable.ic_person_add_material3
                    "booking" -> R.drawable.ic_event_material3
                    else -> R.drawable.ic_notifications_material3
                }
                holder.ivIcon.setImageResource(iconRes)

                // Visual indication for unread
                if (!notification.read) {
                    holder.tvTitle.setTextColor(holder.itemView.context.getColor(R.color.primary))
                } else {
                    holder.tvTitle.setTextColor(holder.itemView.context.getColor(R.color.text_main))
                }

                holder.itemView.setOnClickListener {
                    if (!notification.read) {
                        notification.read = true
                        holder.tvTitle.setTextColor(holder.itemView.context.getColor(R.color.text_main))
                        notification.id?.let { repository.markAsRead(it) }
                    }
                    
                    // Handle deep link if needed
                    notification.type?.let { type ->
                        notification.data?.let { data ->
                            val intent = com.example.taxconnect.core.utils.NotificationHelper.getDeepLinkPendingIntent(
                                holder.itemView.context, type, data
                            )
                            try {
                                intent.send()
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to open deep link")
                            }
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
