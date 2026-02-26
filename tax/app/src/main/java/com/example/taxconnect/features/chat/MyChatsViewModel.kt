package com.example.taxconnect.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.core.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyChatsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _conversationsState = MutableStateFlow<Resource<List<ConversationModel>>>(Resource.Loading())
    val conversationsState: StateFlow<Resource<List<ConversationModel>>> = _conversationsState.asStateFlow()

    private val _blockedUsersState = MutableStateFlow<List<String>>(emptyList())
    val blockedUsersState: StateFlow<List<String>> = _blockedUsersState.asStateFlow()

    fun fetchConversations(uid: String) {
        viewModelScope.launch {
            _conversationsState.value = Resource.Loading()
            try {
                val list = conversationRepository.getConversations(uid)
                _conversationsState.value = Resource.Success(list)
            } catch (e: Exception) {
                _conversationsState.value = Resource.Error(e.message ?: "Failed to load chats")
            }
        }
    }

    fun fetchUser(uid: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.fetchUser(uid)
                if (user.blockedUsers.isNotEmpty()) {
                    _blockedUsersState.value = user.blockedUsers
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}