package com.example.taxconnect.features.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivityChatBinding
import com.example.taxconnect.features.videocall.VideoCallActivity
import com.example.taxconnect.features.chat.MessageAdapter
import android.widget.Toast
import android.net.Uri
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.razorpay.PaymentResultListener
import com.example.taxconnect.features.chat.ChatViewModel
import com.example.taxconnect.core.utils.FileUtils
import javax.inject.Inject
import com.example.taxconnect.data.models.RatingModel
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.common.Resource
import com.example.taxconnect.core.base.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import com.razorpay.Checkout
import org.json.JSONObject
import timber.log.Timber
import android.os.Build
import android.view.View
import com.example.taxconnect.features.chat.dialogs.ProposalDialogHelper
import com.example.taxconnect.features.chat.dialogs.PaymentDialogHelper
import com.example.taxconnect.BuildConfig

@AndroidEntryPoint
class ChatActivity : BaseActivity<ActivityChatBinding>(), PaymentResultListener {

    override val bindingInflater: (LayoutInflater) -> ActivityChatBinding = ActivityChatBinding::inflate
    private val viewModel: ChatViewModel by viewModels()
    @Inject lateinit var fileUtils: FileUtils
    private lateinit var adapter: MessageAdapter
    
    private var currentChatId: String? = null
    private var otherUserId: String? = null
    private var currentUserId: String? = null
    private var otherUserName: String? = null
    
    private var currentAmountToPay = 0.0
    private var currentPaymentDescription = ""
    private var currentPaymentMessage: MessageModel? = null
    private var conversationModel: ConversationModel? = null
    private var hasShownRatingDialog = false
    /** Guards the booking-context snackbar so it is shown at most once per session. */
    private var bookingContextShown = false
    
    private lateinit var proposalDialogHelper: ProposalDialogHelper
    private lateinit var paymentDialogHelper: PaymentDialogHelper

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadFile(it) }
    }

    /** Extracts a human-readable filename from the given URI. */
    private fun getFileName(uri: Uri): String {
        var name: String? = null
        // Try content resolver display name first
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = it.getString(idx)
                }
            }
        }
        // Fall back to last path segment
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        }
        return name!!
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startVideoCall()
        } else {
            showToast(getString(R.string.camera_mic_permission_needed), Toast.LENGTH_SHORT)
        }
    }

    override fun initViews() {
        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().uid
        if (currentUserId == null) {
            finish()
            return
        }
        
        setupHelpers()

        if (intent.hasExtra("bookingId")) {
            val bookingId = intent.getStringExtra("bookingId")
            otherUserId = intent.getStringExtra("otherUserId") ?: intent.getStringExtra("userId")
            otherUserName = intent.getStringExtra("otherUserName") ?: intent.getStringExtra("userName")
            currentChatId = intent.getStringExtra("chatId") // also capture chatId if present
            bookingId?.let { id -> showBookingBannerOnce(id) }
        } else {
            currentChatId = intent.getStringExtra("chatId")
            otherUserId = intent.getStringExtra("otherUserId")
            otherUserName = intent.getStringExtra("otherUserName")
            // No bookingId in intent — the booking banner will be shown automatically
            // once the conversation loads and its bookingId field is read (see observeViewModel)
        }
        
        if (currentChatId == null && otherUserId == null) {
            // Nothing to open — malformed deep-link or missing extras
            showToast(getString(R.string.error_chat_open))
            finish()
            return
        }

        if (currentChatId == null && otherUserId != null) {
             viewModel.initializeChatByUsers(currentUserId!!, otherUserId!!)
        } else if (currentChatId != null && otherUserId != null) {
             viewModel.initializeChat(currentChatId!!, otherUserId!!)
        } else if (currentChatId != null && otherUserId == null) {
            // CA may arrive here from notifications or other sources that only have chatId.
            // Fetch conversation to derive otherUserId from participantIds.
            viewModel.initializeChatById(currentChatId!!, currentUserId!!)
        }

        setupRecyclerView()
        
        // Handle window insets for keyboard and system bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        
        try {
            Checkout.preload(applicationContext)
        } catch (e: Exception) {
            Timber.e(e, "Error preloading Razorpay")
        }
    }

    override fun observeViewModel() {
        // ── Guard: emit from ViewModel if chat cannot be opened (e.g. booking still PENDING) ──
        lifecycleScope.launch {
            viewModel.chatErrorState.collect { errorMsg ->
                showToast(errorMsg)
                finish()
            }
        }

        lifecycleScope.launch {
            viewModel.chatIdResolved.collect { resolvedId ->
                if (resolvedId != null && currentChatId == null) {
                    currentChatId = resolvedId
                }
            }
        }

        lifecycleScope.launch {
            viewModel.messagesState.collect { messages ->
                adapter.setMessages(messages)
                if (messages.isNotEmpty()) {
                    binding.rvChatMessages.scrollToPosition(messages.size - 1)
                    binding.layoutChatEmpty.visibility = View.GONE
                    binding.rvChatMessages.visibility = View.VISIBLE
                } else {
                    binding.layoutChatEmpty.visibility = View.VISIBLE
                    binding.rvChatMessages.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.conversationState.collect { conversation ->
                conversationModel = conversation
                currentChatId = conversation?.conversationId
                updateWorkflowUI(conversation)

                // Auto-show service context banner once when conversation loads
                val bookingId = conversation?.bookingId
                    ?: intent.getStringExtra("bookingId")
                if (!bookingId.isNullOrBlank()) {
                    showBookingBannerOnce(bookingId)
                }

                // Load service history once when conversation ID is first known
                if (conversation?.conversationId != null) {
                    loadServiceHistory()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.otherUserState.collect { resource ->
                if (resource is Resource.Success) {
                    val user = resource.data
                    if (user != null) {
                        binding.toolbar.title = user.name
                        binding.toolbar.subtitle = if (user.isOnline) "Online" else "Offline"
                        otherUserName = user.name
                        // Re-evaluate action row visibility now that the other user's role is known.
                        // conversationState may have fired first (before role was loaded),
                        // so the CA/client button visibility could be wrong without this refresh.
                        updateWorkflowUI(conversationModel)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentUserState.collect { resource ->
                if (resource is Resource.Success && resource.data != null) {
                    // Re-evaluate action row visibility once we know the current user's role.
                    updateWorkflowUI(conversationModel)
                }
            }
        }

        
        lifecycleScope.launch {
            viewModel.fileUploadState.collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        resource.data?.let { url ->
                            sendAttachmentMessage(url, null, pendingFileName)
                            pendingMessageId?.let { viewModel.removePendingMessage(it) }
                            pendingFileName = null
                            pendingMessageId = null
                            viewModel.clearUploadState()
                        }
                    }
                    is Resource.Error -> {
                        showToast(getString(R.string.upload_failed, resource.message))
                        pendingMessageId?.let { viewModel.updatePendingMessageStatus(it, "FAILED") }
                        pendingFileName = null
                        // We do not clear pendingMessageId here so the user can see the "FAILED" bubble
                        viewModel.clearUploadState()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.documentUploadState.collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val (docUrl, thumbUrl) = resource.data ?: Pair("", null)
                        if (docUrl.isNotEmpty()) {
                            sendAttachmentMessage(docUrl, thumbUrl, pendingFileName)
                            pendingMessageId?.let { viewModel.removePendingMessage(it) }
                        }
                        pendingFileName = null
                        pendingMessageId = null
                        viewModel.clearUploadState()
                    }
                    is Resource.Error -> {
                        showToast(getString(R.string.upload_failed, resource.message))
                        pendingMessageId?.let { viewModel.updatePendingMessageStatus(it, "FAILED") }
                        pendingFileName = null
                        viewModel.clearUploadState()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.submitRatingState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        // Handled in dialog button click
                    }
                    is Resource.Success -> {
                        showToast("Thank you for your feedback!", Toast.LENGTH_SHORT)
                        ratingDialog?.dismiss()
                        ratingDialog = null
                        // Conversation state is reset to DISCUSSION inside the submitRating
                        // Firestore transaction — no separate call needed here.
                    }
                    is Resource.Error -> {
                        showToast(resource.message ?: "Failed to submit rating", Toast.LENGTH_SHORT)
                        // Re-enable buttons if error
                        ratingDialog?.let { dialog ->
                            dialog.findViewById<android.view.View>(R.id.btnSubmit)?.isEnabled = true
                            dialog.findViewById<android.view.View>(R.id.btnCancel)?.isEnabled = true
                            dialog.findViewById<android.view.View>(R.id.progressBar)?.visibility = View.GONE
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.sendMessageState.collect { resource ->
                if (resource is Resource.Error) {
                    showToast(resource.message ?: "Failed to send message. Check your connection.")
                }
            }
        }
    }

    private fun setupHelpers() {
        proposalDialogHelper = ProposalDialogHelper(
            this,
            layoutInflater,
            onSendProposal = { message -> viewModel.sendMessage(message) },
            onSendPaymentRequest = { message -> 
                viewModel.sendMessage(message)
                currentChatId?.let { chatId ->
                    viewModel.updateConversationState(chatId, ConversationModel.STATE_PAYMENT_PENDING)
                }
            }
        )

        paymentDialogHelper = PaymentDialogHelper(
            this,
            layoutInflater,
            onPayWithWallet = { amount, message, status ->
                viewModel.payWithWallet(amount, message, status, {
                    showToast(getString(R.string.payment_successful_wallet), Toast.LENGTH_SHORT)
                    Timber.i("Wallet payment successful for amount: $amount")
                }, { error ->
                    showToast(error, Toast.LENGTH_LONG)
                    Timber.e("Wallet payment failed: $error")
                })
            },
            onPayWithGateway = { amount, message ->
                currentPaymentMessage = message
                initiatePayment(amount, message.message ?: getString(R.string.chat_payment))
            },
            onDeclinePayment = { message, reason ->
                val chatId = message.chatId
                val messageId = message.id
                if (chatId != null && messageId != null) {
                    viewModel.updatePaymentStatus(chatId, messageId, "DECLINED", reason)
                }
            }
        )
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(object : MessageAdapter.OnProposalActionListener {
            override fun onAccept(proposal: MessageModel) {
                // Client accepts the CA's proposal — mark ACCEPTED and set stage to ADVANCE_DUE
                val chatId = proposal.chatId
                val proposalId = proposal.id
                if (chatId != null && proposalId != null) {
                    viewModel.updatePaymentStatus(chatId, proposalId, "ACCEPTED", null, "ADVANCE_DUE")
                }
                // Accepting a proposal puts us in ADVANCE_PAYMENT state
                chatId?.let { viewModel.updateConversationState(it, ConversationModel.STATE_ADVANCE_PAYMENT) }
            }
            
            override fun onPayAdvance(proposal: MessageModel) {
                val stage = proposal.proposalPaymentStage
                val amount = if ("ADVANCE_DUE" == stage || "ACCEPTED" == proposal.proposalStatus) {
                    proposal.proposalAdvanceAmount?.toDoubleOrNull() ?: 0.0
                } else {
                    proposal.proposalFinalAmount?.toDoubleOrNull() ?: 0.0
                }
                
                if (amount > 0) {
                    currentPaymentMessage = proposal
                    paymentDialogHelper.showPaymentOptions(proposal)
                } else {
                    val chatId = proposal.chatId
                    val proposalId = proposal.id
                    if (chatId != null && proposalId != null) {
                        if ("ADVANCE_DUE" == stage || "ACCEPTED" == proposal.proposalStatus) {
                            viewModel.updatePaymentStatus(chatId, proposalId, "ADVANCE_PAID")
                        } else {
                            viewModel.updatePaymentStatus(chatId, proposalId, "FINAL_PAID")
                        }
                    }
                }
            }
            override fun onReject(proposal: MessageModel) {
                val chatId = proposal.chatId
                val proposalId = proposal.id
                if (chatId != null && proposalId != null) {
                    viewModel.updatePaymentStatus(chatId, proposalId, "REJECTED")
                }
            }
            override fun onRevise(proposal: MessageModel) {
                proposalDialogHelper.showCreateProposalDialog(currentUserId, otherUserId, currentChatId)
            }
        }, object : MessageAdapter.OnRetryUploadListener {
            override fun onRetry(message: MessageModel) {
                val localId = message.id ?: return
                val localPath = message.localFilePath ?: return
                viewModel.updatePendingMessageStatus(localId, "UPLOADING")
                pendingFileName = message.message
                pendingMessageId = localId
                viewModel.uploadDocumentWithThumbnail(localPath, message.localThumbnailPath)
            }
        })
        
        adapter.setOnCallActionListener(object : MessageAdapter.OnCallActionListener {
            override fun onJoinCall(message: MessageModel) {
                checkPermissionsAndStartCall()
            }
            override fun onRequestAccept(message: MessageModel) {
                viewModel.updateVideoCallPermission(true)
            }
            override fun onRequestReject(message: MessageModel) {
                viewModel.updateVideoCallPermission(false)
            }
        })

        adapter.setOnPaymentActionListener(object : MessageAdapter.OnPaymentActionListener {
            override fun onPay(message: MessageModel) {
                currentPaymentMessage = message
                paymentDialogHelper.showPaymentOptions(message)
            }
            override fun onDecline(message: MessageModel) {
                paymentDialogHelper.showDeclinePaymentDialog(message)
            }
            override fun onRequestAgain(message: MessageModel) {
                proposalDialogHelper.showPaymentRequestDialog(currentUserId, otherUserId, currentChatId)
            }
        })

        adapter.setOnSatisfiedListener(object : MessageAdapter.OnSatisfiedListener {
            override fun onSatisfied(message: MessageModel) {
                val otherUser = (viewModel.otherUserState.value as? Resource.Success)?.data
                if (otherUser != null) {
                    showRateCaDialog(otherUser)
                }
            }
        })

        adapter.setMessages(ArrayList())
        binding.rvChatMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = adapter
        
        // Scroll to bottom when keyboard opens
        binding.rvChatMessages.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && adapter.itemCount > 0) {
                binding.rvChatMessages.post {
                    binding.rvChatMessages.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    override fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnAttach.setOnClickListener {
            showAttachmentOptions()
        }

        binding.btnVideoCall.setOnClickListener {
            checkPermissionsAndStartCall()
        }
        
        binding.btnCreateProposal.setOnClickListener {
            proposalDialogHelper.showCreateProposalDialog(currentUserId, otherUserId, currentChatId)
        }

        binding.chipSuggestion1.setOnClickListener {
            binding.etMessage.setText("👋 Hello! I'm ready to get started")
            binding.etMessage.requestFocus()
        }
        binding.chipSuggestion2.setOnClickListener {
            binding.etMessage.setText("📎 I have some documents to share")
            binding.etMessage.requestFocus()
        }
        binding.chipSuggestion3.setOnClickListener {
            binding.etMessage.setText("❓ I have a few questions first")
            binding.etMessage.requestFocus()
        }

        // ── Action Row Buttons ─────────────────────────────────────────────────
        binding.btnBookAppointment.setOnClickListener {
            val otherUser = (viewModel.otherUserState.value as? Resource.Success)?.data
            if (otherUser != null) {
                val intent = Intent(this, com.example.taxconnect.features.booking.BookAppointmentActivity::class.java)
                intent.putExtra("CA_DATA", otherUser)
                startActivity(intent)
            }
        }
        binding.btnCustomerVideoCall.setOnClickListener {
            checkPermissionsAndStartCall()
        }
        binding.btnLeaveFeedback.setOnClickListener {
            val otherUser = (viewModel.otherUserState.value as? Resource.Success)?.data
            if (otherUser != null) showRateCaDialog(otherUser)
        }
        binding.btnRequestAgain.setOnClickListener {
            // Client wants to book the CA again — open BookAppointmentActivity
            val otherUser = (viewModel.otherUserState.value as? Resource.Success)?.data
            if (otherUser != null) {
                val intent = Intent(this, com.example.taxconnect.features.booking.BookAppointmentActivity::class.java)
                intent.putExtra("CA_DATA", otherUser)
                startActivity(intent)
            }
        }
        binding.btnRequestDocs.setOnClickListener {
            binding.etMessage.setText("📄 Please share the required documents")
            binding.etMessage.requestFocus()
        }
        // CA: complete the booking; sends invoice summary to client and marks conversation COMPLETED
        binding.btnCompleteJob.setOnClickListener {
            val proposal = adapter.getLatestAcceptedProposal()
            val advance = proposal?.proposalAdvanceAmount ?: "0"
            val final   = proposal?.proposalFinalAmount   ?: "0"
            // Pull service name from the proposal description (e.g. "ITR Filing – Basic")
            val serviceName = proposal?.proposalDescription ?: ""
            viewModel.sendServiceSummaryAndComplete(advance, final, serviceName)
        }

        // Payment issue card actions
        binding.btnPaymentRetry.setOnClickListener {
            binding.cardPaymentIssue.visibility = View.GONE
            currentPaymentMessage?.let { paymentDialogHelper.showPaymentOptions(it) }
        }
        binding.btnPaymentSupport.setOnClickListener {
            binding.cardPaymentIssue.visibility = View.GONE
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:support@taxconnect.com")
                putExtra(Intent.EXTRA_SUBJECT, "Payment Support Needed")
            }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else showToast("Please email support@taxconnect.com")
        }

        // Header revise button
        binding.btnReviseHeader.setOnClickListener {
            proposalDialogHelper.showCreateProposalDialog(currentUserId, otherUserId, currentChatId)
        }

        // Pay Final — triggers payment dialog scoped to the final payment amount
        binding.btnPayFinal.setOnClickListener {
            val proposal = adapter.getLatestAcceptedProposal()
            if (proposal != null) {
                currentPaymentMessage = proposal
                paymentDialogHelper.showPaymentOptions(proposal)
            } else {
                showToast(getString(R.string.no_pending_payment))
            }
        }

        // Toggle Video Call — CA-side: enable video and broadcast a system message to the chat
        binding.btnToggleVideoCall.setOnClickListener {
            viewModel.updateVideoCallPermission(true)
            viewModel.sendSystemMessage(getString(R.string.system_msg_video_enabled))
            showToast(getString(R.string.video_call_enabled))
        }

        // View History — expand/collapse the past-bookings card
        binding.btnViewHistory.setOnClickListener {
            binding.layoutServiceHistory.visibility =
                if (binding.layoutServiceHistory.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return
        val cid = currentChatId ?: run { showToast("Chat is loading, please wait..."); return }
        val uid = currentUserId ?: run { showToast("Not signed in"); return }

        val message = MessageModel(
            senderId = uid,
            receiverId = otherUserId,
            chatId = cid,
            message = text,
            timestamp = System.currentTimeMillis(),
            type = "TEXT"
        )
        viewModel.sendMessage(message)
        binding.etMessage.setText("")
    }

    private fun generatePdfThumbnail(uri: Uri): java.io.File? {
        try {
            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return null
            val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            
            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                // Render at a reasonable resolution (e.g., 800px width)
                val width = 800
                val height = (width.toFloat() / page.width * page.height).toInt()
                
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                // Fill with white background first
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pdfRenderer.close()
                fileDescriptor.close()

                // Save to cache
                val thumbnailFile = java.io.File(cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                val outStream = java.io.FileOutputStream(thumbnailFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outStream)
                outStream.flush()
                outStream.close()
                bitmap.recycle()
                
                return thumbnailFile
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating PDF thumbnail")
        }
        return null
    }

    private var pendingFileName: String? = null
    private var pendingMessageId: String? = null

    private fun uploadFile(uri: Uri) {
        val cid = currentChatId ?: run { showToast("Chat is loading, please wait..."); return }
        val fileName = getFileName(uri)
        pendingFileName = fileName
        
        val cachedFile = fileUtils.copyFileToCache(uri, fileName)
        
        if (cachedFile != null) {
            val isPdf = contentResolver.getType(uri)?.contains("pdf") == true || fileName.endsWith(".pdf", ignoreCase = true)
            var thumbnailFile: java.io.File? = null
            
            if (isPdf) {
                thumbnailFile = generatePdfThumbnail(uri)
            }
            
            // Add optimistic bubble with local file paths for retry capability
            val localId = "local_${System.currentTimeMillis()}"
            pendingMessageId = localId
            val type = if (contentResolver.getType(uri)?.startsWith("image") == true) "IMAGE" else "DOCUMENT"
            
            val optimisticMessage = MessageModel(
                senderId = currentUserId,
                receiverId = otherUserId,
                chatId = cid,
                message = fileName,
                timestamp = System.currentTimeMillis(),
                type = type,
                id = localId,
                uploadStatus = "UPLOADING",
                localFilePath = cachedFile.absolutePath,
                localThumbnailPath = thumbnailFile?.absolutePath
            )
            viewModel.addPendingMessage(optimisticMessage)
            
            viewModel.uploadDocumentWithThumbnail(cachedFile.absolutePath, thumbnailFile?.absolutePath)
        } else {
            showToast(getString(R.string.cannot_get_file_path))
        }
    }
    
    private fun sendAttachmentMessage(url: String, thumbnailUrl: String? = null, fileName: String? = null) {
        val mimeType = contentResolver.getType(Uri.parse(url)) ?: ""
        val isImage = url.contains(".jpg", ignoreCase = true)
                || url.contains(".jpeg", ignoreCase = true)
                || url.contains(".png", ignoreCase = true)
                || url.contains(".gif", ignoreCase = true)
                || url.contains(".webp", ignoreCase = true)
                || mimeType.startsWith("image/")
        val type = if (isImage) "IMAGE" else "DOCUMENT"

        // For DOCUMENT: message = readable filename, documentUrl = download URL, imageUrl = PDF thumbnail
        // For IMAGE:    imageUrl = image URL, message = url (not shown, imageUrl is used for display)
        val displayName = if (type == "DOCUMENT") {
            fileName?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Document" }
        } else null

        val message = MessageModel(
            senderId = currentUserId,
            receiverId = otherUserId,
            chatId = currentChatId,
            message = if (type == "DOCUMENT") displayName else url,
            timestamp = System.currentTimeMillis(),
            type = type
        )
        if (type == "IMAGE") {
            message.imageUrl = url
        } else {
            message.documentUrl = url          // dedicated field for the doc download URL
            message.imageUrl = thumbnailUrl    // PDF thumbnail (may be null for non-PDF docs)
        }

        viewModel.sendMessage(message)

        // If document is sent after advance payment, update state to docs pending for final payment
        if (type == "DOCUMENT" && conversationModel?.workflowState == ConversationModel.STATE_ADVANCE_PAYMENT) {
            currentChatId?.let {
                viewModel.updateConversationState(it, ConversationModel.STATE_DOCS_PENDING)
            }
        }
    }

    private fun updateWorkflowUI(conversation: ConversationModel?) {
        if (conversation == null) return
        
        val state = conversation.workflowState
        binding.tvWorkflowStatus.text = "Status: ${getWorkflowLabel(state)}"
        
        val showStepper = state in listOf(
            ConversationModel.STATE_PAYMENT_PENDING,
            ConversationModel.STATE_ADVANCE_PAYMENT,
            ConversationModel.STATE_DOCS_PENDING,
            ConversationModel.STATE_FINAL_DUE,
            ConversationModel.STATE_READY_TO_COMPLETE,
            ConversationModel.STATE_COMPLETED
        )
        
        // Hide the revise button by default; only shown in PAYMENT_PENDING
        binding.btnReviseHeader.visibility = View.GONE
        
        if (showStepper) {
            binding.tvPaymentProgress.visibility = View.GONE
            binding.layoutStepper.visibility = View.VISIBLE
            binding.layoutStepperLabels.visibility = View.VISIBLE
            
            // Reset stepper to default inactive state
            binding.step1Icon.setImageResource(R.drawable.ic_check_circle)
            binding.step1Line.alpha = 0.3f
            binding.step2Icon.setImageResource(R.drawable.ic_circle_outline)
            binding.step2Line.alpha = 0.3f
            binding.step3Icon.setImageResource(R.drawable.ic_circle_outline)
            binding.tvStep1Label.alpha = 1.0f
            binding.tvStep2Label.alpha = 0.5f
            binding.tvStep3Label.alpha = 0.5f
            binding.tvWorkflowNext.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            
            when (state) {
                ConversationModel.STATE_PAYMENT_PENDING, ConversationModel.STATE_ADVANCE_PAYMENT -> {
                    // Proposal accepted — waiting for (or just received) advance payment
                    if (state == ConversationModel.STATE_PAYMENT_PENDING) {
                        binding.step1Icon.setImageResource(R.drawable.ic_circle_outline)
                        binding.tvWorkflowNext.text = getString(R.string.next_client_pay)
                        binding.btnReviseHeader.visibility = View.VISIBLE
                    } else {
                        // Advance paid but CA hasn't started yet (shouldn't linger here long)
                        binding.tvWorkflowNext.text = getString(R.string.next_ca_work)
                    }
                }
                ConversationModel.STATE_DOCS_PENDING -> {
                    // Advance paid; CA is working and will send docs + request final payment
                    binding.step1Line.alpha = 1.0f
                    binding.step2Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.tvStep2Label.alpha = 1.0f
                    binding.tvWorkflowNext.text = "CA is working — docs & final payment request coming"
                }
                ConversationModel.STATE_FINAL_DUE -> {
                    // CA has sent docs & asked for final payment; client pays now
                    binding.step1Line.alpha = 1.0f
                    binding.step2Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.step2Line.alpha = 1.0f
                    binding.step3Icon.setImageResource(R.drawable.ic_circle_outline)
                    binding.tvStep2Label.alpha = 1.0f
                    binding.tvStep3Label.alpha = 0.4f
                    binding.tvWorkflowNext.text = "Final payment is due — pay to complete the service"
                }
                ConversationModel.STATE_READY_TO_COMPLETE -> {
                    // Final paid; CA must click "Complete Booking"
                    binding.step1Line.alpha = 1.0f
                    binding.step2Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.step2Line.alpha = 1.0f
                    binding.step3Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.tvStep2Label.alpha = 1.0f
                    binding.tvStep3Label.alpha = 1.0f
                    binding.tvWorkflowNext.text = "All payments received — awaiting CA to complete booking"
                }
                ConversationModel.STATE_COMPLETED -> {
                    // Everything done
                    binding.step1Line.alpha = 1.0f
                    binding.step2Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.step2Line.alpha = 1.0f
                    binding.step3Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.tvStep2Label.alpha = 1.0f
                    binding.tvStep3Label.alpha = 1.0f
                    binding.tvWorkflowNext.text = getString(R.string.status_project_completed)
                    binding.tvWorkflowNext.setTextColor(ContextCompat.getColor(this, R.color.status_online))
                }
            }
        } else {
            binding.tvPaymentProgress.visibility = View.VISIBLE
            binding.layoutStepper.visibility = View.GONE
            binding.layoutStepperLabels.visibility = View.GONE
            
            when (state) {
                ConversationModel.STATE_DISCUSSION -> {
                    binding.tvPaymentProgress.text = getString(R.string.payment_no_active_request)
                    binding.tvWorkflowNext.text = getString(R.string.next_send_proposal)
                }
                ConversationModel.STATE_REQUESTED -> {
                    binding.tvPaymentProgress.text = getString(R.string.payment_not_started)
                    binding.tvWorkflowNext.text = getString(R.string.next_accept_decline)
                }
                else -> {
                    binding.tvPaymentProgress.text = getString(R.string.payment_not_started)
                    binding.tvWorkflowNext.text = getString(R.string.next_continue_discussion)
                }
            }
        }
        // ── Action Row: show context-sensitive buttons ─────────────────────────
        val otherUser = (viewModel.otherUserState.value as? Resource.Success)?.data
        val currentUser = (viewModel.currentUserState.value as? Resource.Success)?.data
        
        // Hide action buttons while user roles are loading to prevent flashing wrong UI
        if (currentUser == null && otherUser == null) {
            binding.btnCreateProposal.visibility  = View.GONE
            binding.btnRequestDocs.visibility     = View.GONE
            binding.btnCompleteJob.visibility     = View.GONE
            binding.btnToggleVideoCall.visibility = View.GONE
            binding.btnBookAppointment.visibility     = View.GONE
            binding.btnCustomerVideoCall.visibility   = View.GONE
            binding.btnLeaveFeedback.visibility       = View.GONE
            binding.btnRequestAgain.visibility        = View.GONE
            binding.btnPayFinal.visibility            = View.GONE
            binding.btnViewHistory.visibility         = View.GONE
            return
        }

        // Determine roles dynamically
        val isCA = currentUser?.role?.equals("CA", ignoreCase = true) == true
        val isClient = currentUser?.role?.equals("CUSTOMER", ignoreCase = true) == true || 
                       currentUser?.role?.equals("CLIENT", ignoreCase = true) == true
                       
        // isTalkingToCA = the current user is a client chatting with a CA
        val isTalkingToCA = isClient || otherUser?.role?.equals("CA", ignoreCase = true) == true
        
        val isCompleted      = state == ConversationModel.STATE_COMPLETED
        val isDiscussion     = state == ConversationModel.STATE_DISCUSSION || state == null
        val isDocsPending    = state == ConversationModel.STATE_DOCS_PENDING
        val isReadyComplete  = state == ConversationModel.STATE_READY_TO_COMPLETE
        val isFinalDue       = state == ConversationModel.STATE_FINAL_DUE

        // ── CA-side buttons ────────────────────────────────────────────────────
        // Create Proposal: only in Discussion
        binding.btnCreateProposal.visibility    = if (!isTalkingToCA && isDiscussion) View.VISIBLE else View.GONE
        // Request Docs: CA reminds client to send docs (DOCS_PENDING phase)
        binding.btnRequestDocs.visibility       = if (!isTalkingToCA && isDocsPending) View.VISIBLE else View.GONE
        // Complete Booking: only shown when final payment is received
        binding.btnCompleteJob.visibility       = if (!isTalkingToCA && isReadyComplete) View.VISIBLE else View.GONE
        // Video call enable: only in Discussion
        binding.btnToggleVideoCall.visibility   = if (!isTalkingToCA && isDiscussion) View.VISIBLE else View.GONE

        // ── Client-side buttons ───────────────────────────────────────────────
        binding.btnBookAppointment.visibility   = if (isTalkingToCA && isDiscussion) View.VISIBLE else View.GONE
        binding.btnCustomerVideoCall.visibility = View.GONE  // toolbar btnVideoCall handles this
        // Leave Feedback: manual option even after COMPLETED
        binding.btnLeaveFeedback.visibility     = if (isTalkingToCA && isCompleted) View.VISIBLE else View.GONE
        binding.btnRequestAgain.visibility      = if (isTalkingToCA && isCompleted) View.VISIBLE else View.GONE
        // Pay Final: big "Pay Final" button shown when CA has requested final payment
        binding.btnPayFinal.visibility          = if (isTalkingToCA && isFinalDue) View.VISIBLE else View.GONE
        binding.btnViewHistory.visibility       = if (isTalkingToCA && isCompleted) View.VISIBLE else View.GONE
        
        // ── Empty State Handling ─────────────────────────────────────────
        val hasBooking = conversationModel?.bookingId != null
        if (hasBooking) {
            binding.tvChatEmptyTitle.text = "Your booking is confirmed!"
            binding.tvChatEmptySubtitle.text = "Introduce yourself and share any documents the CA may need."
            binding.chipSuggestion1.text = "👋 Hello! I'm ready to get started"
        } else {
            binding.tvChatEmptyTitle.text = if (isTalkingToCA) "Start a conversation" else "A client wants to connect"
            binding.tvChatEmptySubtitle.text = if (isTalkingToCA) 
                "Ask a question or request a service from this professional." 
            else 
                "Say hello and discuss their requirements."
            binding.chipSuggestion1.text = if (isTalkingToCA) "👋 Hi, I have a question about your services." else "👋 Hello! How can I help you today?"
        }
    }


    private fun getWorkflowLabel(state: String?): String {
        if (state.isNullOrBlank()) return "Discussion"
        // If it contains underscores, it's a raw enum like ADVANCE_PAYMENT — convert to title case
        if (state.contains("_")) {
            return state.split("_").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
        return state
    }

    private var ratingDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showRateCaDialog(ca: UserModel) {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_rate_ca)
            .setCancelable(false)
            .create()
        ratingDialog = dialog
        dialog.show()

        val ratingBar = dialog.findViewById<android.widget.RatingBar>(R.id.ratingBar)
        val etFeedback = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFeedback)
        val btnSubmit = dialog.findViewById<android.view.View>(R.id.btnSubmit)
        val btnCancel = dialog.findViewById<android.view.View>(R.id.btnCancel)
        val progressBar = dialog.findViewById<android.view.View>(R.id.progressBar)

        btnCancel?.setOnClickListener { 
            ratingDialog = null
            dialog.dismiss() 
        }

        btnSubmit?.setOnClickListener {
            val ratingValue = ratingBar?.rating ?: 0f
            if (ratingValue == 0f) {
                showToast("Please select a rating")
                return@setOnClickListener
            }

            val feedback = etFeedback?.text.toString().trim()
            
            val currentUser = (viewModel.currentUserState.value as? Resource.Success)?.data
            val ratingModel = RatingModel(
                userId = currentUserId,
                userName = currentUser?.name ?: "Client",
                caId = ca.uid,
                rating = ratingValue,
                review = feedback,
                timestamp = System.currentTimeMillis()
            )

            viewModel.submitRating(ratingModel)
            // Don't dismiss yet, wait for observer
            btnSubmit.isEnabled = false
            btnCancel?.isEnabled = false
            progressBar?.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionsAndStartCall() {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted  = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && audioGranted) {
            startVideoCall()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startVideoCall() {
        val chatId = currentChatId ?: run { showToast("Chat not ready yet, please wait..."); return }
        val intent = Intent(this, VideoCallActivity::class.java)
        intent.putExtra("CHANNEL_NAME", chatId)
        intent.putExtra("ROOM_UUID", chatId)
        intent.putExtra("CALLER_NAME", otherUserName)

        // Reset the call status before starting to prevent instant finish from previous calls
        com.example.taxconnect.data.repositories.ConversationRepository.getInstance()
            .updateCallStatus(chatId, "INITIATED", null)

        // Notify the other user via Firestore that a call has started
        viewModel.sendCallMessage()

        startActivity(intent)
    }

    private fun showAttachmentOptions() {
        val currentUser = (viewModel.currentUserState.value as? Resource.Success)?.data
        val isCA = currentUser?.role == "CA"

        val optionsList = ArrayList<String>()
        optionsList.add(getString(R.string.attachment_image))
        optionsList.add(getString(R.string.attachment_document))
        if (isCA) {
            optionsList.add(getString(R.string.attachment_payment_request))
        }

        val options = optionsList.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.send_attachment))
            .setItems(options) { _, which ->
                val selection = options[which]
                when (selection) {
                    getString(R.string.attachment_image) -> filePickerLauncher.launch("image/*")
                    getString(R.string.attachment_document) -> filePickerLauncher.launch("*/*")
                    getString(R.string.attachment_payment_request) -> proposalDialogHelper.showPaymentRequestDialog(currentUserId, otherUserId, currentChatId)
                }
            }
            .show()
    }

    private fun initiatePayment(amount: Double, description: String) {
        currentAmountToPay = amount
        currentPaymentDescription = description
        
        val checkout = Checkout()
        checkout.setKeyID(BuildConfig.RAZORPAY_API_KEY)
        
        try {
            val options = JSONObject()
            options.put("name", "Tax Connect")
            options.put("description", description)
            options.put("currency", "INR")
            options.put("amount", amount * 100)
            checkout.open(this, options)
        } catch (e: Exception) {
            showToast(getString(R.string.error_in_payment, e.message))
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        showToast(getString(R.string.payment_successful_gateway), Toast.LENGTH_SHORT)
        
        currentPaymentMessage?.let { msg ->
            val status = when {
                msg.type == "PROPOSAL" -> {
                    if ("ADVANCE_DUE" == msg.proposalPaymentStage) "ADVANCE_PAID" else "FINAL_PAID"
                }
                msg.type == "PAYMENT_REQUEST" -> {
                    if ("ADVANCE" == msg.paymentStage) "ADVANCE_PAID" else "FINAL_PAID"
                }
                else -> "PAID"
            }
            
            if (razorpayPaymentId != null) {
                val amount = currentAmountToPay
                viewModel.processExternalPayment(amount, msg, status, razorpayPaymentId, {
                     Timber.i("External payment processed successfully")
                }, { error ->
                     showToast(getString(R.string.payment_status_update_failed, error))
                })
            } else {
                msg.chatId?.let { chatId ->
                    msg.id?.let { msgId ->
                        viewModel.updatePaymentStatus(chatId, msgId, status)
                    }
                }
            }
            
            currentPaymentMessage = null
            binding.cardPaymentIssue.visibility = View.GONE
        }
    }


    override fun onPaymentError(code: Int, response: String?) {
        val errorMessage = response ?: getString(R.string.error_unknown)
        binding.cardPaymentIssue.visibility = View.VISIBLE
        binding.tvPaymentIssue.text = getString(R.string.payment_failed_message, errorMessage)
    }


    /**
     * Loads all past bookings between currentUser and otherUser and shows them
     * in the cardServiceHistory panel.
     */
    private fun loadServiceHistory() {
        val uid = currentUserId ?: return
        val other = otherUserId ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Two targeted queries: one where current user is the client, one where they are the CA.
        // Firestore's whereIn on a field only matches one role at a time.
        val clientQuery = db.collection("bookings")
            .whereEqualTo("userId", uid).whereEqualTo("caId", other)
        val caQuery = db.collection("bookings")
            .whereEqualTo("userId", other).whereEqualTo("caId", uid)

        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(clientQuery.get(), caQuery.get())
            .addOnSuccessListener { results ->
                val matching = results
                    .flatMap { (it as com.google.firebase.firestore.QuerySnapshot).documents }
                    .mapNotNull { it.toObject(com.example.taxconnect.data.models.BookingModel::class.java) }
                    .sortedByDescending { it.appointmentTimestamp }

                if (matching.isEmpty()) {
                    binding.tvServiceHistoryEmpty.visibility = View.VISIBLE
                    binding.layoutServiceHistory.removeAllViews()
                } else {
                    binding.tvServiceHistoryEmpty.visibility = View.GONE
                    binding.layoutServiceHistory.removeAllViews()
                    matching.forEach { booking ->
                        val row = android.widget.TextView(this).apply {
                            val status = booking.status?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                            text = "• ${booking.serviceName ?: "Service"} — ${booking.appointmentDate ?: ""} [$status]"
                            textSize = 13f
                            setTextColor(androidx.core.content.ContextCompat.getColor(this@ChatActivity, R.color.text_secondary))
                            setPadding(0, 4, 0, 4)
                        }
                        binding.layoutServiceHistory.addView(row)
                    }
                    binding.cardServiceHistory.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to load service history")
            }
    }

    /**
     * Shows the booking-context snackbar at most once per Activity lifecycle.
     * Guards via [bookingContextShown] so re-renders and observer re-fires don't spam.
     */
    private fun showBookingBannerOnce(bookingId: String) {
        if (bookingContextShown) return
        bookingContextShown = true
        fetchAndShowBookingContextBanner(bookingId)
    }

    private fun fetchAndShowBookingContextBanner(bookingId: String) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("bookings").document(bookingId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                val service = doc.getString("serviceName") ?: return@addOnSuccessListener
                val date = doc.getString("appointmentDate") ?: ""
                val time = doc.getString("appointmentTime") ?: ""
                val status = doc.getString("status") ?: ""

                val msg = buildString {
                    append("📋 $service")
                    if (date.isNotBlank()) append(" · $date")
                    if (time.isNotBlank()) append(" $time")
                    if (status.isNotBlank()) append(" · ${status.lowercase().replaceFirstChar { it.uppercase() }}")
                }

                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
    }
}
