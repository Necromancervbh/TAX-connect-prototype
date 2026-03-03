package com.example.taxconnect.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _caListState = MutableStateFlow<Resource<List<UserModel>>>(Resource.Loading())
    val caListState: StateFlow<Resource<List<UserModel>>> = _caListState.asStateFlow()

    private val _userState = MutableStateFlow<Resource<UserModel>>(Resource.Loading())
    val userState: StateFlow<Resource<UserModel>> = _userState.asStateFlow()

    private val _unreadCountState = MutableStateFlow<Int>(0)
    val unreadCountState: StateFlow<Int> = _unreadCountState.asStateFlow()

    fun fetchCAs() {
        viewModelScope.launch {
            _caListState.value = Resource.Loading()
            try {
                val list = userRepository.getCAList()
                _caListState.value = Resource.Success(list)
            } catch (e: Exception) {
                _caListState.value = Resource.Error(e.message ?: "Failed to fetch CAs")
            }
        }
    }

    fun fetchUser(uid: String) {
        viewModelScope.launch {
            _userState.value = Resource.Loading()
            try {
                val user = userRepository.fetchUser(uid)
                _userState.value = Resource.Success(user)
                
                // Start listening to unread count
                listenToUnreadCount(uid)
            } catch (e: Exception) {
                _userState.value = Resource.Error(e.message ?: "Failed to fetch profile")
            }
        }
    }

    private fun listenToUnreadCount(uid: String) {
        viewModelScope.launch {
            conversationRepository.getUnreadCountFlow(uid).collect { count ->
                _unreadCountState.value = count
            }
        }
    }
}