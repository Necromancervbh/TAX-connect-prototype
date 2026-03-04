package com.example.taxconnect.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DashboardRepository
import com.example.taxconnect.data.repositories.WalletRepository
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.common.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CADashboardViewModel @Inject constructor(
    private val repository: DashboardRepository
) : ViewModel() {

    private val _userState = MutableStateFlow<Resource<UserModel>>(Resource.Loading())
    val userState: StateFlow<Resource<UserModel>> = _userState.asStateFlow()

    private val _revenueState = MutableStateFlow<Double>(0.0)
    val revenueState: StateFlow<Double> = _revenueState.asStateFlow()

    private val _revenueBreakdownState = MutableStateFlow(WalletRepository.RevenueBreakdown(0.0, 0.0, 0.0))
    val revenueBreakdownState: StateFlow<WalletRepository.RevenueBreakdown> = _revenueBreakdownState.asStateFlow()

    private val _clientStatsState = MutableStateFlow(ClientStats())
    val clientStatsState: StateFlow<ClientStats> = _clientStatsState.asStateFlow()

    private val _messageStatsState = MutableStateFlow(MessageStats())
    val messageStatsState: StateFlow<MessageStats> = _messageStatsState.asStateFlow()

    private val _bookingsState = MutableStateFlow<Resource<List<BookingModel>>>(Resource.Loading())
    val bookingsState: StateFlow<Resource<List<BookingModel>>> = _bookingsState.asStateFlow()

    private val _walletState = MutableStateFlow<Double>(0.0)
    val walletState: StateFlow<Double> = _walletState.asStateFlow()

    private val _statusUpdateState = MutableStateFlow<Resource<Boolean>>(Resource.Success(false))
    val statusUpdateState: StateFlow<Resource<Boolean>> = _statusUpdateState.asStateFlow()
    
    data class ClientStats(val active: Int = 0, val returning: Int = 0)
    data class MessageStats(val unread: Int = 0)

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    fun loadDashboardData(uid: String) {
        _errorState.value = null
        fetchUser(uid)
        fetchRevenue(uid)
        fetchClientStats(uid)
        listenToBookings(uid)
        fetchWallet(uid)
        listenToUnreadMessages(uid)
    }

    /** Re-fetches lightweight data on resume without restarting live listeners. */
    fun refreshData(uid: String) {
        fetchUser(uid)
        fetchRevenue(uid)
        fetchClientStats(uid)
        fetchWallet(uid)
    }

    private fun listenToUnreadMessages(uid: String) {
        viewModelScope.launch {
            try {
                repository.getUnreadCountFlow(uid).collect { count ->
                    _messageStatsState.value = MessageStats(count)
                }
            } catch (e: Exception) {
                // Ignore flow failure gracefully
            }
        }
    }

    fun fetchUser(uid: String) {
        viewModelScope.launch {
            try {
                val user = repository.fetchUser(uid)
                _userState.value = Resource.Success(user)
            } catch (e: Exception) {
                _userState.value = Resource.Error(e.message ?: "Failed to fetch user")
                _errorState.value = e.message
            }
        }
    }

    private fun fetchRevenue(uid: String) {
        viewModelScope.launch {
            try {
                _revenueState.value = repository.getRevenueStats(uid)
                _revenueBreakdownState.value = repository.getRevenueBreakdown(uid)
            } catch (e: Exception) {
                _errorState.value = "Failed to load revenue data"
            }
        }
    }

    private fun fetchClientStats(uid: String) {
        viewModelScope.launch {
            try {
                val conversations = repository.getConversations(uid)
                var returning = 0
                var active = 0
                var unreadTotal = 0
                
                for (conv in conversations) {
                    if (conv.serviceCycleSequence > 1) returning++
                    val state = conv.workflowState
                    if (state != ConversationModel.STATE_REQUESTED && state != ConversationModel.STATE_REFUSED) {
                        active++
                    }
                    
                    // Count unread - removed, handled by real-time flow
                }
                _clientStatsState.value = ClientStats(active, returning)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }


    fun listenToBookings(uid: String) {
        viewModelScope.launch {
            _bookingsState.value = Resource.Loading()
            try {
                repository.getBookingsForCaFlow(uid).collect { bookings ->
                    _bookingsState.value = Resource.Success(bookings)
                }
            } catch (e: Exception) {
                _bookingsState.value = Resource.Error(e.message ?: "Failed to load bookings")
            }
        }
    }

    private fun fetchWallet(uid: String) {
        viewModelScope.launch {
            try {
                _walletState.value = repository.getWalletBalance(uid)
            } catch (e: Exception) {
                _walletState.value = 0.0
            }
        }
    }

    fun updateUserStatus(uid: String, isOnline: Boolean) {
        viewModelScope.launch {
            _statusUpdateState.value = Resource.Loading()
            try {
                repository.updateUserStatus(uid, isOnline)
                _statusUpdateState.value = Resource.Success(isOnline)
                
                // Update local user state as well to keep UI in sync
                val currentUser = _userState.value.data
                if (currentUser != null) {
                    _userState.value = Resource.Success(currentUser.copy(isOnline = isOnline))
                }
            } catch (e: Exception) {
                _statusUpdateState.value = Resource.Error(e.message ?: "Update failed")
            }
        }
    }

    fun updateBookingStatus(booking: BookingModel, status: String) {
        viewModelScope.launch {
            try {
                val bookingId = booking.id ?: return@launch
                val caId = booking.caId ?: return@launch
                val userId = booking.userId ?: return@launch

                repository.updateBookingStatus(bookingId, status)
                if (status == "ACCEPTED") {
                    repository.incrementClientCount(caId, userId)
                }
                // Live listener in listenToBookings() handles refresh automatically
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private val _acceptBookingState = MutableStateFlow<Resource<String?>>(Resource.Success(null))
    val acceptBookingState: StateFlow<Resource<String?>> = _acceptBookingState.asStateFlow()

    fun acceptBookingWithChat(booking: BookingModel, onConvId: (String) -> Unit, onError: (String) -> Unit) {
        val caId = booking.caId ?: return
        viewModelScope.launch {
            _acceptBookingState.value = Resource.Loading()
            try {
                val convId = repository.acceptBookingWithChat(booking)
                _acceptBookingState.value = Resource.Success(convId)
                if (convId != null) onConvId(convId)
                // Live listener in listenToBookings() handles refresh automatically
            } catch (e: Exception) {
                _acceptBookingState.value = Resource.Error(e.message ?: "Failed to accept booking")
                onError(e.message ?: "Failed to accept booking")
            }
        }
    }

    fun rejectBooking(booking: BookingModel, reason: String?) {
        val bookingId = booking.id ?: return
        viewModelScope.launch {
            try {
                repository.rejectBooking(bookingId, reason)
                // Live listener in listenToBookings() handles refresh automatically
            } catch (_: Exception) { }
        }
    }

    fun autoExpirePendingBookings(caUid: String) {
        viewModelScope.launch {
            try { repository.autoExpirePendingBookings(caUid) } catch (_: Exception) { }
        }
    }

    /**
     * Marks the booking as COMPLETED and also transitions the linked conversation
     * to STATE_COMPLETED so the client sees the stepper finish and gets the rating prompt.
     */
    fun completeBookingAndConversation(booking: BookingModel) {
        val bookingId = booking.id ?: return
        val chatId   = booking.chatId
        viewModelScope.launch {
            try {
                repository.updateBookingStatus(bookingId, "COMPLETED")
                if (!chatId.isNullOrBlank()) {
                    // Directly update conversation state to COMPLETED to trigger client's
                    // workflow stepper and rating dialog in ChatActivity.
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("conversations")
                        .document(chatId)
                        .update("workflowState", ConversationModel.STATE_COMPLETED)
                }
            } catch (_: Exception) { }
        }
    }




}
