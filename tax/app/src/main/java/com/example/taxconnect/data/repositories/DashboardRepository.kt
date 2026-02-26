package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.UserModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val walletRepository: WalletRepository,
    private val conversationRepository: ConversationRepository,
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository
) {

    suspend fun getRevenueStats(uid: String): Double {
        return walletRepository.getRevenueStats(uid)
    }

    suspend fun getRevenueBreakdown(uid: String): WalletRepository.RevenueBreakdown {
        return walletRepository.getRevenueBreakdown(uid)
    }

    suspend fun getConversations(uid: String): List<ConversationModel> {
        return conversationRepository.getConversations(uid)
    }

    suspend fun getRequests(uid: String): List<ConversationModel> {
        return conversationRepository.getRequests(uid)
    }

    suspend fun getBookingsForCA(uid: String): List<BookingModel> {
        return bookingRepository.getBookingsForCA(uid)
    }

    suspend fun getWalletBalance(uid: String): Double {
        return walletRepository.getWalletBalance(uid)
    }

    suspend fun fetchUser(uid: String): UserModel {
        return userRepository.fetchUser(uid)
    }

    suspend fun updateUserStatus(uid: String, isOnline: Boolean) {
        userRepository.updateUserStatus(uid, isOnline)
    }

    suspend fun updateBookingStatus(id: String, status: String) {
        bookingRepository.updateBookingStatus(id, status)
    }

    suspend fun incrementClientCount(caId: String, userId: String) {
        bookingRepository.incrementClientCount(caId, userId)
    }
}