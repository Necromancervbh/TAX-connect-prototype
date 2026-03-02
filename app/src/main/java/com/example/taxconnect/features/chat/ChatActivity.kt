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
        } else {
            currentChatId = intent.getStringExtra("chatId")
            otherUserId = intent.getStringExtra("otherUserId")
            otherUserName = intent.getStringExtra("otherUserName")
        }
        
        if (currentChatId == null && otherUserId != null) {
             viewModel.initializeChatByUsers(currentUserId!!, otherUserId!!)
        } else if (currentChatId != null && otherUserId != null) {
             viewModel.initializeChat(currentChatId!!, otherUserId!!)
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
                    }
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
                        // Reset conversation state back to Discussion for future services
                        currentChatId?.let { chatId ->
                            viewModel.updateConversationState(chatId, ConversationModel.STATE_DISCUSSION)
                        }
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
                // Determine next stage
                val nextStage = if (proposal.proposalAmount?.toDoubleOrNull() ?: 0.0 > 0) {
                    "ADVANCE_DUE" // Assuming advance is needed if there's an amount
                } else {
                    "FINAL_PAID" // Or whatever stage makes sense for 0 amount
                }
                
                val chatId = proposal.chatId
                val proposalId = proposal.id
                if (chatId != null && proposalId != null) {
                    viewModel.updatePaymentStatus(chatId, proposalId, "ACCEPTED", null, nextStage)
                }
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
                    // No amount to pay, just update status
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
                
                // Update local message state back to UPLOADING
                viewModel.updatePendingMessageStatus(localId, "UPLOADING")
                
                // Set pending state back up
                pendingFileName = message.message
                pendingMessageId = localId
                
                // Restart upload process
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
            binding.etMessage.setText("👋 Hello, I need help with my ITR")
            binding.etMessage.requestFocus()
        }
        binding.chipSuggestion2.setOnClickListener {
            binding.etMessage.setText("📊 I need help with GST filing")
            binding.etMessage.requestFocus()
        }
        binding.chipSuggestion3.setOnClickListener {
            binding.etMessage.setText("📄 I received a tax notice")
            binding.etMessage.requestFocus()
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val message = MessageModel(
            currentUserId,
            otherUserId,
            currentChatId,
            text,
            System.currentTimeMillis(),
            "TEXT"
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
                chatId = currentChatId,
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
            currentUserId,
            otherUserId,
            currentChatId,
            if (type == "DOCUMENT") displayName else url,
            System.currentTimeMillis(),
            type
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
            ConversationModel.STATE_COMPLETED
        )
        
        if (showStepper) {
            binding.tvPaymentProgress.visibility = View.GONE
            binding.layoutStepper.visibility = View.VISIBLE
            binding.layoutStepperLabels.visibility = View.VISIBLE
            
            // Default inactive
            binding.step1Icon.setImageResource(R.drawable.ic_check_circle)
            binding.step1Line.alpha = 0.3f
            binding.step2Icon.setImageResource(R.drawable.ic_circle_outline)
            binding.step2Line.alpha = 0.3f
            binding.step3Icon.setImageResource(R.drawable.ic_circle_outline)
            
            // Reset text styling
            binding.tvStep1Label.alpha = 1.0f
            binding.tvStep2Label.alpha = 0.5f
            binding.tvStep3Label.alpha = 0.5f
            
            when (state) {
                ConversationModel.STATE_PAYMENT_PENDING -> {
                    // Proposal sent/accepted, waiting for advance payment
                    binding.step1Icon.setImageResource(R.drawable.ic_circle_outline) // pending completion of step 1
                    binding.tvWorkflowNext.text = getString(R.string.next_client_pay)
                }
                ConversationModel.STATE_ADVANCE_PAYMENT, ConversationModel.STATE_DOCS_PENDING -> {
                    // Advance paid
                    binding.step1Line.alpha = 1.0f
                    binding.step2Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.tvStep2Label.alpha = 1.0f
                    
                    if (state == ConversationModel.STATE_ADVANCE_PAYMENT) {
                        binding.tvWorkflowNext.text = getString(R.string.next_ca_work)
                    } else {
                        binding.tvWorkflowNext.text = getString(R.string.next_final_request)
                    }
                }
                ConversationModel.STATE_COMPLETED -> {
                    // Fully paid
                    binding.step1Line.alpha = 1.0f
                    binding.step2Icon.setImageResource(R.drawable.ic_check_circle)
                    binding.step2Line.alpha = 1.0f
                    binding.step3Icon.setImageResource(R.drawable.ic_check_circle)
                    
                    binding.tvStep2Label.alpha = 1.0f
                    binding.tvStep3Label.alpha = 1.0f
                    
                    binding.tvWorkflowNext.text = getString(R.string.status_project_completed)
                    binding.tvWorkflowNext.setTextColor(ContextCompat.getColor(this, R.color.status_online))
                    
                    // Trigger rating dialog for client (only once per session)
                    val otherUser = (viewModel.otherUserState.value as? Resource.Success)?.data
                    if (otherUser?.role == "CA" && !hasShownRatingDialog && ratingDialog == null) {
                        hasShownRatingDialog = true
                        showRateCaDialog(otherUser)
                    }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA) 
                return
            }
        }
        startVideoCall()
    }

    private fun startVideoCall() {
        val intent = Intent(this, VideoCallActivity::class.java)
        intent.putExtra("CHANNEL_NAME", currentChatId)
        intent.putExtra("ROOM_UUID", currentChatId)
        intent.putExtra("CALLER_NAME", otherUserName)
        
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
        showToast("Payment Successful")
        
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
                     showToast("Failed to update payment status: $error")
                })
            } else {
                msg.chatId?.let { chatId ->
                    msg.id?.let { msgId ->
                        viewModel.updatePaymentStatus(chatId, msgId, status)
                    }
                }
            }
            
            currentPaymentMessage = null
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        val errorMessage = response ?: "Unknown error"
        com.google.android.material.snackbar.Snackbar.make(binding.root, "Payment Failed: $errorMessage", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                currentPaymentMessage?.let { paymentDialogHelper.showPaymentOptions(it) }
            }.show()
    }
}