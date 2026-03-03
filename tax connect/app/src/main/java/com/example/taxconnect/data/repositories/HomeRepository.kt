package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.UserModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository
) {

    suspend fun getCAList(): List<UserModel> {
        return userRepository.getCAList()
    }

    suspend fun fetchUser(uid: String): UserModel {
        return userRepository.fetchUser(uid)
    }

    suspend fun getTotalUnreadCount(uid: String): Int {
        val conversations = conversationRepository.getConversations(uid)
        return conversations.sumOf { conv ->
            @Suppress("UNCHECKED_CAST")
            val unreadCounts = (conv.unreadCounts as? Map<String, Long>) ?: emptyMap()
            (unreadCounts[uid] ?: 0).toInt()
        }
    }
}