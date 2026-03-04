package com.example.taxconnect.core.navigation

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.features.*
import com.example.taxconnect.features.dashboard.CADashboardActivity
import com.example.taxconnect.features.home.HomeActivity
import com.example.taxconnect.features.wallet.WalletActivity
import com.example.taxconnect.features.profile.ProfileActivity
import com.example.taxconnect.features.chat.ChatActivity
import com.example.taxconnect.features.booking.MyBookingsActivity
import com.example.taxconnect.features.documents.MyDocumentsActivity
import com.example.taxconnect.features.booking.OrderHistoryActivity


import com.example.taxconnect.features.community.CommunityActivity
import com.example.taxconnect.features.videocall.VideoCallActivity
import com.example.taxconnect.features.ca.CADetailActivity
import com.example.taxconnect.features.community.PostDetailActivity
import com.example.taxconnect.features.milestones.MilestonesActivity
import com.example.taxconnect.features.ca.ExploreCAsActivity
import com.example.taxconnect.features.auth.LoginActivity
import com.example.taxconnect.features.notification.NotificationHistoryActivity
import com.example.taxconnect.features.notification.NotificationSettingsActivity
import com.example.taxconnect.features.documents.BalanceSheetActivity
import com.example.taxconnect.features.documents.SecureDocViewerActivity
import com.example.taxconnect.features.videocall.VideoPlayerActivity

class NavigationManager {

    fun navigateToLogin(activity: Activity) {
        activity.startActivity(Intent(activity, LoginActivity::class.java))
        activity.finish()
    }

    fun navigateToDashboard(activity: Activity, user: UserModel) {
        val intent = if (user.role == "CA") {
            Intent(activity, CADashboardActivity::class.java)
        } else {
            Intent(activity, HomeActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }

    fun navigateToWallet(activity: Activity) {
        activity.startActivity(Intent(activity, WalletActivity::class.java))
    }

    fun navigateToProfile(activity: Activity, userId: String? = null) {
        val intent = Intent(activity, ProfileActivity::class.java)
        userId?.let { intent.putExtra("USER_ID", it) }
        activity.startActivity(intent)
    }

    fun navigateToExploreCAs(activity: Activity) {
        activity.startActivity(Intent(activity, ExploreCAsActivity::class.java))
    }

    fun navigateToChat(activity: Activity, conversationId: String, otherUserId: String, otherUserName: String) {
        val intent = Intent(activity, ChatActivity::class.java).apply {
            putExtra("chatId", conversationId)
            putExtra("otherUserId", otherUserId)
            putExtra("otherUserName", otherUserName)
        }
        activity.startActivity(intent)
    }

    fun navigateToMyBookings(activity: Activity) {
        activity.startActivity(Intent(activity, MyBookingsActivity::class.java))
    }

    fun navigateToMyDocuments(activity: Activity) {
        activity.startActivity(Intent(activity, MyDocumentsActivity::class.java))
    }

    fun navigateToOrderHistory(activity: Activity) {
        activity.startActivity(Intent(activity, OrderHistoryActivity::class.java))
    }

    fun navigateToPendingBookings(activity: Activity) {
        activity.startActivity(Intent(activity, MyBookingsActivity::class.java))
    }

    fun navigateToCommunity(activity: Activity) {
        activity.startActivity(Intent(activity, CommunityActivity::class.java))
    }

    fun navigateToVideoCall(activity: Activity, channelName: String, token: String, isCaller: Boolean) {
        val intent = Intent(activity, VideoCallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("TOKEN", token)
            putExtra("IS_CALLER", isCaller)
        }
        activity.startActivity(intent)
    }

    fun navigateToCADetail(activity: Activity, caId: String) {
        val intent = Intent(activity, CADetailActivity::class.java)
        intent.putExtra("CA_ID", caId)
        activity.startActivity(intent)
    }

    fun navigateToCADetail(activity: Activity, ca: UserModel, showRequestDialog: Boolean = false) {
        val intent = Intent(activity, CADetailActivity::class.java)
        intent.putExtra("CA_DATA", ca)
        if (showRequestDialog) {
            intent.putExtra("SHOW_REQUEST_DIALOG", true)
        }
        activity.startActivity(intent)
    }

    fun navigateToPostDetail(activity: Activity, postId: String) {
        val intent = Intent(activity, PostDetailActivity::class.java)
        intent.putExtra("POST_ID", postId)
        activity.startActivity(intent)
    }

    fun navigateToMilestones(activity: Activity) {
        activity.startActivity(Intent(activity, MilestonesActivity::class.java))
    }

    fun navigateToNotifications(activity: Activity) {
        activity.startActivity(Intent(activity, NotificationHistoryActivity::class.java))
    }

    fun navigateToBalanceSheet(activity: Activity) {
        activity.startActivity(Intent(activity, BalanceSheetActivity::class.java))
    }

    fun navigateToNotificationSettings(activity: Activity) {
        activity.startActivity(Intent(activity, NotificationSettingsActivity::class.java))
    }

    fun navigateToSecureDocViewer(activity: Activity, documentId: String) {
        val intent = Intent(activity, SecureDocViewerActivity::class.java)
        intent.putExtra("DOCUMENT_ID", documentId)
        activity.startActivity(intent)
    }

    fun navigateToVideoPlayer(activity: Activity, videoUrl: String, title: String) {
        val intent = Intent(activity, VideoPlayerActivity::class.java).apply {
            putExtra("VIDEO_URL", videoUrl)
            putExtra("TITLE", title)
        }
        activity.startActivity(intent)
    }
}