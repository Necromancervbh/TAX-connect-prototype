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
import androidx.core.content.ContextCompat

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
        
        notifications.sortedByDescending { it.timestamp }.forEach {
            when {
                it.timestamp >= todayStart    -> todayList.add(it)
                it.timestamp >= yesterdayStart -> yesterdayList.add(it)
                else                           -> olderList.add(it)
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

    /** Human-friendly relative time: "Just now", "5 min ago", "2:34 PM", "Mar 1" */
    private fun relativeTime(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000L      -> "Just now"
            diff < 3_600_000L   -> "${diff / 60_000} min ago"
            diff < 86_400_000L  -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
            else                -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        }
    }

    sealed class NotificationListItem {
        data class HeaderItem(val title: String) : NotificationListItem()
        data class NotifItem(val notification: NotificationModel) : NotificationListItem()
    }

    inner class NotificationAdapter(private val items: List<NotificationListItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_HEADER = 0
        private val VIEW_TYPE_ITEM   = 1

        override fun getItemViewType(position: Int) =
            if (items[position] is NotificationListItem.HeaderItem) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeaderTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
        }

        inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon:    ImageView = view.findViewById(R.id.ivIcon)
            val iconBg:    View      = view.findViewById(R.id.iconBg)
            val unreadBar: View      = view.findViewById(R.id.unreadBar)
            val tvTitle:   TextView  = view.findViewById(R.id.tvTitle)
            val tvBody:    TextView  = view.findViewById(R.id.tvBody)
            val tvTime:    TextView  = view.findViewById(R.id.tvTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_HEADER) {
                HeaderViewHolder(inflater.inflate(R.layout.item_notification_header, parent, false))
            } else {
                NotificationViewHolder(inflater.inflate(R.layout.item_notification, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]

            if (holder is HeaderViewHolder && item is NotificationListItem.HeaderItem) {
                holder.tvHeaderTitle.text = item.title
                return
            }

            if (holder !is NotificationViewHolder || item !is NotificationListItem.NotifItem) return
            val notification = item.notification
            val ctx = holder.itemView.context

            // --- Resolve display title + body ---
            var displayTitle = notification.title
            val displayBody  = notification.body
            if (notification.data != null) {
                when (notification.type) {
                    "message" -> notification.data["senderName"]
                        ?.takeIf { it.isNotEmpty() }?.let { displayTitle = it }
                    "call"    -> notification.data["callerName"]
                        ?.takeIf { it.isNotEmpty() }?.let {
                            displayTitle = getString(R.string.notification_call_from, it)
                        }
                }
            }
            holder.tvTitle.text = displayTitle ?: getString(R.string.notification_default_title)
            holder.tvBody.text  = displayBody ?: ""
            holder.tvTime.text  = relativeTime(notification.timestamp)

            // --- Icon + tinted background per notification type ---
            data class TypeStyle(val icon: Int, val bgColor: Int, val iconColor: Int)
            val style = when (notification.type) {
                "message"  -> TypeStyle(R.drawable.ic_chat_bubble_material3,
                                        R.color.md_theme_light_secondaryContainer, R.color.secondary)
                "call"     -> TypeStyle(R.drawable.ic_video_call_material3,
                                        R.color.md_theme_light_secondaryContainer, R.color.secondary)
                "request"  -> TypeStyle(R.drawable.ic_person_add_material3,
                                        R.color.error_container, R.color.error)
                "booking"  -> TypeStyle(R.drawable.ic_event_material3, 0, 0) // orange handled below
                else       -> TypeStyle(R.drawable.ic_notifications_material3,
                                        R.color.surface_variant, R.color.text_muted)
            }

            holder.ivIcon.setImageResource(style.icon)
            if (notification.type == "booking") {
                holder.iconBg.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFFFFF3E0.toInt()) // amber 50
                holder.ivIcon.imageTintList =
                    android.content.res.ColorStateList.valueOf(0xFFF97316.toInt()) // orange 500
            } else {
                holder.iconBg.backgroundTintList  = ContextCompat.getColorStateList(ctx, style.bgColor)
                holder.ivIcon.imageTintList        = ContextCompat.getColorStateList(ctx, style.iconColor)
            }

            // --- Unread accent bar visibility ---
            holder.unreadBar.visibility = if (!notification.read) View.VISIBLE else View.GONE

            // --- Unread title emphasis: full opacity + main color; read: dimmed ---
            if (!notification.read) {
                holder.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_main))
                holder.tvTitle.alpha = 1f
            } else {
                holder.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                holder.tvTitle.alpha = 0.75f
            }

            // --- Click: mark read + deep link ---
            holder.itemView.setOnClickListener {
                if (!notification.read) {
                    notification.read = true
                    holder.unreadBar.visibility = View.GONE
                    holder.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    holder.tvTitle.alpha = 0.75f
                    notification.id?.let { id -> repository.markAsRead(id) }
                }
                notification.type?.let { type ->
                    notification.data?.let { data ->
                        val intent = com.example.taxconnect.core.utils.NotificationHelper
                            .getDeepLinkPendingIntent(ctx, type, data)
                        try { intent.send() } catch (e: Exception) {
                            timber.log.Timber.e(e, "Failed to open deep link")
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
