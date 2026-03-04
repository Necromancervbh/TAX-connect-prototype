package com.example.taxconnect.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.data.models.RatingModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.common.Resource
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

import com.example.taxconnect.data.repositories.WalletRepository
import timber.log.Timber

class ChatViewModel(
    private val conversationRepository: ConversationRepository = ConversationRepository.getInstance(),
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val walletRepository: WalletRepository = WalletRepository.getInstance()
) : ViewModel() {

    private val _messagesState = MutableStateFlow<List<MessageModel>>(emptyList())
    
    // Holds local, unsent optimistic messages (like "Uploading...")
    private val _pendingMessages = MutableStateFlow<List<MessageModel>>(emptyList())
    
    // Combines Firestore messages with local pending messages
    val messagesState: StateFlow<List<MessageModel>> = combine(
        _messagesState,
        _pendingMessages
    ) { firestoreMsgs, pendingMsgs ->
        // Sort both by timestamp ascending
        (firestoreMsgs + pendingMsgs).sortedBy { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _conversationState = MutableStateFlow<ConversationModel?>(null)
    val conversationState: StateFlow<ConversationModel?> = _conversationState.asStateFlow()

    private val _otherUserState = MutableStateFlow<Resource<UserModel>>(Resource.Loading())
    val otherUserState: StateFlow<Resource<UserModel>> = _otherUserState.asStateFlow()

    private val _currentUserState = MutableStateFlow<Resource<UserModel>>(Resource.Loading())
    val currentUserState: StateFlow<Resource<UserModel>> = _currentUserState.asStateFlow()

    private val _sendMessageState = MutableSharedFlow<Resource<Unit>>()
    val sendMessageState: SharedFlow<Resource<Unit>> = _sendMessageState.asSharedFlow()
    
    private val _fileUploadState = MutableStateFlow<Resource<String>>(Resource.Loading(null)) // URL
    val fileUploadState: StateFlow<Resource<String>> = _fileUploadState.asStateFlow()

    private val _submitRatingState = MutableSharedFlow<Resource<Unit>>()
    val submitRatingState: SharedFlow<Resource<Unit>> = _submitRatingState.asSharedFlow()

    /** Emitted when chat cannot be initialized (e.g. no conversation exists yet). ChatActivity observes and finishes gracefully. */
    private val _chatErrorState = MutableSharedFlow<String>(replay = 0)
    val chatErrorState: SharedFlow<String> = _chatErrorState.asSharedFlow()

    private val _chatIdResolved = MutableStateFlow<String?>(null)
    val chatIdResolved: StateFlow<String?> = _chatIdResolved.asStateFlow()

    private var currentChatId: String? = null

    fun initializeChat(chatId: String, otherUserId: String) {
        currentChatId = chatId
        _chatIdResolved.value = chatId
        fetchOtherUser(otherUserId)
        fetchCurrentUser()
        observeMessages(chatId)
        observeConversation(chatId)
        
        // Mark as read when entering chat
        markAsRead()
    }
    
    fun initializeChatByUsers(currentUid: String, otherUid: String) {
        viewModelScope.launch {
            val existingChatId = conversationRepository.findExistingChatId(currentUid, otherUid)
            if (existingChatId != null) {
                initializeChat(existingChatId, otherUid)
            } else {
                // If it doesn't exist yet, we still want to open the chat window 
                // using the deterministic ID so messages can be sent.
                // The conversation document will be created seamlessly on first message.
                val deterministicId = conversationRepository.getChatId(currentUid, otherUid)
                initializeChat(deterministicId, otherUid)
            }
        }
    }

    /**
     * Used when only chatId is known (e.g. CA opens chat from notification with just chatId).
     * Fetches the conversation to derive otherUserId from participantIds, then initializes chat.
     */
    fun initializeChatById(chatId: String, currentUid: String) {
        viewModelScope.launch {
            try {
                val conv = conversationRepository.getConversationOnce(chatId)
                val participantIds = conv?.participantIds
                val otherUid = participantIds?.firstOrNull { uid: String -> uid != currentUid }
                if (otherUid != null) {
                    initializeChat(chatId, otherUid)
                } else {
                    // Fallback: still observe messages and conversation even without otherUserId
                    currentChatId = chatId
                    fetchCurrentUser()
                    observeMessages(chatId)
                    observeConversation(chatId)
                    markAsRead()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve otherUserId for chatId=$chatId")
                // Emit error so ChatActivity can finish gracefully instead of loading forever
                _chatErrorState.emit("Couldn't open chat. Please try again.")
            }
        }
    }

    private fun markAsRead() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid ?: return
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            conversationRepository.markMessagesAsRead(chatId, uid)
        }
    }

    private fun fetchCurrentUser() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid ?: return
        viewModelScope.launch {
            userRepository.observeUser(uid)
                .catch { e ->
                    _currentUserState.value = Resource.Error(e.message ?: "User not found")
                }
                .collect { user ->
                    _currentUserState.value = Resource.Success(user)
                }
        }
    }

    private fun fetchOtherUser(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid)
                .catch { e ->
                    _otherUserState.value = Resource.Error(e.message ?: "User not found")
                }
                .collect { user ->
                    _otherUserState.value = Resource.Success(user)
                }
        }
    }

    private fun observeMessages(chatId: String) {
        viewModelScope.launch {
            conversationRepository.getMessagesFlow(chatId)
                .catch { _ ->
                    // Handle error (maybe emit to a separate error flow)
                }
                .collect { messages ->
                    _messagesState.value = messages
                }
        }
    }

    private fun observeConversation(chatId: String) {
        viewModelScope.launch {
            conversationRepository.getConversationFlow(chatId)
                .catch { _ ->
                    // Handle error
                }
                .collect { conversation ->
                    _conversationState.value = conversation
                }
        }
    }

    fun sendMessage(message: MessageModel) {
        viewModelScope.launch {
            try {
                conversationRepository.sendMessage(message)
                _sendMessageState.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                _sendMessageState.emit(Resource.Error(e.message ?: "Failed to send"))
            }
        }
    }

    fun updateConversationState(chatId: String, state: String) {
        viewModelScope.launch {
            try {
                conversationRepository.updateConversationState(chatId, state)
            } catch (e: Exception) {
                Timber.e(e, "Error updating conversation state to $state for chat $chatId")
            }
        }
    }

    fun sendCallMessage() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid ?: return
        val otherUserId = (_otherUserState.value as? Resource.Success)?.data?.uid ?: return
        val message = MessageModel(
            senderId = uid,
            receiverId = otherUserId,
            chatId = currentChatId,
            message = "Incoming Video Call...",
            timestamp = System.currentTimeMillis(),
            type = "CALL"
        )
        sendMessage(message)
    }

    /**
     * Posts a neutral system message visible to both participants.
     * Uses senderId = "SYSTEM" so it renders as a centered pill, not a chat bubble.
     */
    fun sendSystemMessage(text: String) {
        val chatId = currentChatId ?: return
        val message = MessageModel(
            senderId = "SYSTEM",
            receiverId = null,
            chatId = chatId,
            message = text,
            timestamp = System.currentTimeMillis(),
            type = "SYSTEM"
        )
        sendMessage(message)
    }


    fun submitRating(rating: RatingModel) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            _submitRatingState.emit(Resource.Loading())
            try {
                // Use submitRating which atomically writes the rating subcollection doc,
                // updates the CA's aggregate rating, and resets conversation state — all
                // in a single Firestore transaction.
                com.example.taxconnect.data.repositories.DataRepository.getInstance()
                    .submitRating(rating, chatId, object : com.example.taxconnect.data.repositories.DataRepository.DataCallback<Void?> {
                        override fun onSuccess(data: Void?) {
                            viewModelScope.launch { _submitRatingState.emit(Resource.Success(Unit)) }
                        }
                        override fun onError(error: String?) {
                            viewModelScope.launch { _submitRatingState.emit(Resource.Error(error ?: "Unknown error")) }
                        }
                    })
            } catch (e: Exception) {
                _submitRatingState.emit(Resource.Error(e.message ?: "Rating submission failed"))
            }
        }
    }

    fun uploadFile(filePath: String) {
        viewModelScope.launch {
            _fileUploadState.value = Resource.Loading()
            try {
                val url = conversationRepository.uploadFile(filePath)
                _fileUploadState.value = Resource.Success(url)
            } catch (e: Exception) {
                _fileUploadState.value = Resource.Error(e.message ?: "Upload failed")
            }
        }
    }
    
    // Custom state flow for document + thumbnail concurrent upload
    private val _documentUploadState = MutableStateFlow<Resource<Pair<String, String?>>>(Resource.Loading(null))
    val documentUploadState: StateFlow<Resource<Pair<String, String?>>> = _documentUploadState.asStateFlow()

    fun uploadDocumentWithThumbnail(documentPath: String, thumbnailPath: String?) {
        viewModelScope.launch {
            _documentUploadState.value = Resource.Loading()
            try {
                // Use async on the CoroutineScope directly instead of kotlinx.coroutines.async explicitly
                val docDeferred = async { conversationRepository.uploadFile(documentPath) }
                val thumbDeferred = thumbnailPath?.let { path -> 
                    async { conversationRepository.uploadFile(path) } 
                }
                
                val docUrl = docDeferred.await()
                val thumbUrl = thumbDeferred?.await()
                
                _documentUploadState.value = Resource.Success(Pair(docUrl, thumbUrl))
            } catch (e: Exception) {
                _documentUploadState.value = Resource.Error(e.message ?: "Document upload failed")
            }
        }
    }
    
    fun clearUploadState() {
        _fileUploadState.value = Resource.Loading(null) // Reset
        _documentUploadState.value = Resource.Loading(null)
    }

    // --- Optimistic UI Methods ---
    
    fun addPendingMessage(message: MessageModel) {
        val currentList = _pendingMessages.value.toMutableList()
        // If message has no ID, assign a local one
        if (message.id == null) {
            message.id = "local_${System.currentTimeMillis()}"
        }
        currentList.add(message)
        _pendingMessages.value = currentList
    }

    fun removePendingMessage(localId: String) {
        val currentList = _pendingMessages.value.toMutableList()
        currentList.removeAll { it.id == localId }
        _pendingMessages.value = currentList
    }
    
    fun updatePendingMessageStatus(localId: String, status: String) {
        val currentList = _pendingMessages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == localId }
        if (index != -1) {
            val msg = currentList[index].copy()
            msg.uploadStatus = status
            currentList[index] = msg
            _pendingMessages.value = currentList
        }
    }

    fun updateConversationState(state: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try {
                conversationRepository.updateConversationState(chatId, state)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updateVideoCallPermission(allowed: Boolean) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try {
                conversationRepository.updateVideoCallPermission(chatId, allowed)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun blockUser(currentUid: String, targetUid: String) {
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUid, targetUid)
                // Optionally update UI
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun payWithWallet(amount: Double, message: MessageModel, status: String = "PAID", onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val payerId = message.receiverId ?: return@launch
                val payeeId = message.senderId ?: return@launch
                val chatId = message.chatId ?: return@launch
                val messageId = message.id ?: return@launch
                
                // Use the atomic processWalletPayment from WalletRepository
                walletRepository.processWalletPayment(
                    payerId, 
                    payeeId, 
                    amount, 
                    "Payment for ${message.proposalDescription ?: "Request"}", 
                    chatId, 
                    messageId, 
                    status
                )

                // Advance the workflow to the correct next phase:
                // ADVANCE_PAID → DOCS_PENDING
                // FINAL_PAID / PAID → READY_TO_COMPLETE
                val nextState = when (status) {
                    "ADVANCE_PAID" -> ConversationModel.STATE_DOCS_PENDING
                    else           -> ConversationModel.STATE_READY_TO_COMPLETE
                }
                conversationRepository.updateConversationState(chatId, nextState)
                
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Payment Failed")
            }
        }
    }

    fun processExternalPayment(amount: Double, message: MessageModel, status: String, transactionId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val payerId = message.receiverId ?: return@launch
                val payeeId = message.senderId ?: return@launch
                val chatId = message.chatId ?: return@launch
                val messageId = message.id ?: return@launch
                
                walletRepository.processExternalPayment(
                    payerId,
                    payeeId,
                    amount,
                    "Payment for ${message.proposalDescription ?: "Request"}",
                    chatId,
                    messageId,
                    status,
                    transactionId
                )

                // Advance the workflow to the correct next phase:
                // ADVANCE_PAID → DOCS_PENDING
                // FINAL_PAID / PAID → READY_TO_COMPLETE
                val nextState = when (status) {
                    "ADVANCE_PAID" -> ConversationModel.STATE_DOCS_PENDING
                    else           -> ConversationModel.STATE_READY_TO_COMPLETE
                }
                conversationRepository.updateConversationState(chatId, nextState)
                
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Processing Failed")
            }
        }
    }
    
    fun updatePaymentStatus(chatId: String, messageId: String, status: String, declineReason: String? = null, stage: String? = null) {
        viewModelScope.launch {
            try {
                conversationRepository.updatePaymentStatus(chatId, messageId, status, declineReason, stage)
                
                // Advance the workflow to the correct next phase:
                // ADVANCE_PAID → DOCS_PENDING (CA works, files taxes, sends docs)
                // FINAL_PAID / PAID → READY_TO_COMPLETE (CA must manually complete)
                if (status == "PAID" || status == "ADVANCE_PAID" || status == "FINAL_PAID") {
                    val state = when (status) {
                        "ADVANCE_PAID" -> ConversationModel.STATE_DOCS_PENDING
                        else           -> ConversationModel.STATE_READY_TO_COMPLETE
                    }
                    conversationRepository.updateConversationState(chatId, state)
                } else if (status == "DECLINED") {
                    conversationRepository.updateConversationState(chatId, ConversationModel.STATE_DISCUSSION)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating payment status to $status")
            }
        }
    }

    /**
     * Called when the CA has finished the work and wants to request the final payment.
     * Transitions the conversation to FINAL_DUE and sends a system message so the
     * client knows the final payment is now due.
     */
    fun transitionToFinalDue() {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try {
                conversationRepository.updateConversationState(chatId, ConversationModel.STATE_FINAL_DUE)
                sendSystemMessage("✅ Work is done! The CA has requested the final payment. Please pay to complete the service.")
            } catch (e: Exception) {
                Timber.e(e, "Error transitioning to FINAL_DUE")
            }
        }
    }

    /**
     * Atomically marks the conversation COMPLETED and sends a SERVICE_SUMMARY message
     * to the client summarising what was paid and what documents were exchanged.
     *
     * @param advanceAmount  Advance amount paid (as string, e.g. "1500")
     * @param finalAmount    Final amount paid (as string, e.g. "3000")
     * @param docsSent       Names of docs sent by CA, comma-separated
     * @param docsReceived   Names of docs received from client, comma-separated
     */
    fun sendServiceSummaryAndComplete(
        advanceAmount: String,
        finalAmount: String,
        serviceName: String = "",
        docsSent: String = "",
        docsReceived: String = ""
    ) {
        val chatId = currentChatId ?: return
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid ?: return
        val otherUid = (_otherUserState.value as? Resource.Success)?.data?.uid ?: return

        viewModelScope.launch {
            try {
                // 1. Mark conversation as COMPLETED first
                conversationRepository.updateConversationState(chatId, ConversationModel.STATE_COMPLETED)

                // 2. Build summary message payload
                val summaryMessage = com.example.taxconnect.data.models.MessageModel(
                    senderId = uid,
                    receiverId = otherUid,
                    chatId = chatId,
                    message = "Service Completed",
                    timestamp = System.currentTimeMillis(),
                    type = "SERVICE_SUMMARY"
                ).apply {
                    proposalAdvanceAmount = advanceAmount
                    proposalFinalAmount = finalAmount
                    this.serviceName = serviceName.ifBlank { null }
                }
                conversationRepository.sendMessage(summaryMessage)

                Timber.i("Service summary sent and conversation marked COMPLETED for chat $chatId")
            } catch (e: Exception) {
                Timber.e(e, "Error completing service for chat $chatId")
            }
        }
    }
}