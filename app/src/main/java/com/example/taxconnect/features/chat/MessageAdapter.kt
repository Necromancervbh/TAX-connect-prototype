package com.example.taxconnect.features.chat

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.core.base.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val proposalListener: OnProposalActionListener? = null,
    private val retryUploadListener: OnRetryUploadListener? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messages: MutableList<MessageModel> = ArrayList()
    private val currentUserId: String? = FirebaseAuth.getInstance().uid
    private var callListener: OnCallActionListener? = null
    private var paymentListener: OnPaymentActionListener? = null

    interface OnProposalActionListener {
        fun onAccept(proposal: MessageModel)
        fun onReject(proposal: MessageModel)
        fun onRevise(proposal: MessageModel)
        fun onPayAdvance(proposal: MessageModel)
    }
    
    interface OnCallActionListener {
        fun onJoinCall(message: MessageModel)
        fun onRequestAccept(message: MessageModel)
        fun onRequestReject(message: MessageModel)
    }
    
    interface OnPaymentActionListener {
        fun onPay(message: MessageModel)
        fun onDecline(message: MessageModel)
        fun onRequestAgain(message: MessageModel)
    }

    interface OnRetryUploadListener {
        fun onRetry(message: MessageModel)
    }
    
    fun setOnCallActionListener(listener: OnCallActionListener) {
        this.callListener = listener
    }
    
    fun setOnPaymentActionListener(listener: OnPaymentActionListener) {
        this.paymentListener = listener
    }

    fun setMessages(messages: List<MessageModel>) {
        this.messages = messages.toMutableList()
        notifyDataSetChanged()
    }
    
    fun addMessage(message: MessageModel) {
        this.messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when (message.type) {
            "PROPOSAL" -> VIEW_TYPE_PROPOSAL
            "CALL_REQUEST" -> VIEW_TYPE_CALL_REQUEST
            "PAYMENT_REQUEST" -> VIEW_TYPE_PAYMENT_REQUEST
            else -> if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PROPOSAL -> ProposalViewHolder(inflater.inflate(R.layout.item_message_proposal, parent, false))
            VIEW_TYPE_CALL_REQUEST -> CallRequestViewHolder(inflater.inflate(R.layout.item_message_call_request, parent, false))
            VIEW_TYPE_PAYMENT_REQUEST -> PaymentRequestViewHolder(inflater.inflate(R.layout.item_message_payment_request, parent, false))
            VIEW_TYPE_SENT -> SentMessageViewHolder(inflater.inflate(R.layout.item_chat_sent, parent, false))
            else -> ReceivedMessageViewHolder(inflater.inflate(R.layout.item_chat_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is ProposalViewHolder -> holder.bind(message)
            is CallRequestViewHolder -> holder.bind(message)
            is PaymentRequestViewHolder -> holder.bind(message)
            is SentMessageViewHolder -> holder.bind(message, callListener)
            is ReceivedMessageViewHolder -> holder.bind(message, callListener)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    private fun isCallMessage(message: MessageModel): Boolean {
        return "CALL" == message.type || "call" == message.type
    }

    inner class PaymentRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvStatusMessage: TextView = itemView.findViewById(R.id.tvStatusMessage)
        private val tvStage: TextView = itemView.findViewById(R.id.tvStage)
        private val tvDeclineReason: TextView = itemView.findViewById(R.id.tvDeclineReason)
        private val btnPay: Button = itemView.findViewById(R.id.btnPay)
        private val btnDecline: Button = itemView.findViewById(R.id.btnDecline)
        private val btnRequestAgain: Button = itemView.findViewById(R.id.btnRequestAgain)
        private val layoutActions: View = itemView.findViewById(R.id.layoutActions)

        fun bind(message: MessageModel) {
            val amount = message.paymentRequestAmount
            val status = message.paymentRequestStatus
            val stage = message.paymentStage ?: "PAYMENT"
            val declineReason = message.paymentDeclineReason
            
            tvAmount.text = itemView.context.getString(R.string.currency_format, amount ?: "0.00")
            tvDescription.text = message.message
            tvStage.text = stage
            
            val isSender = message.senderId == currentUserId
            val isReceiver = message.receiverId == currentUserId
            
            // Reset visibilities
            layoutActions.visibility = View.GONE
            tvStatusMessage.visibility = View.GONE
            tvDeclineReason.visibility = View.GONE
            btnRequestAgain.visibility = View.GONE

            when (status) {
                "PENDING" -> {
                    if (isReceiver) {
                        layoutActions.visibility = View.VISIBLE
                        btnPay.setOnClickListener { paymentListener?.onPay(message) }
                        btnDecline.setOnClickListener { paymentListener?.onDecline(message) }
                    } else {
                        tvStatusMessage.visibility = View.VISIBLE
                        tvStatusMessage.text = itemView.context.getString(R.string.payment_pending_client)
                        val orangeColor = ContextCompat.getColor(itemView.context, R.color.orange_500)
                        tvStatusMessage.setTextColor(orangeColor)
                        tvStatusMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            androidx.core.graphics.ColorUtils.setAlphaComponent(orangeColor, 30)
                        )
                    }
                }
                "PAID", "ADVANCE_PAID", "FINAL_PAID" -> {
                    tvStatusMessage.visibility = View.VISIBLE
                    tvStatusMessage.text = itemView.context.getString(R.string.payment_status_paid)
                    val greenColor = ContextCompat.getColor(itemView.context, R.color.status_online)
                    tvStatusMessage.setTextColor(greenColor)
                    tvStatusMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(greenColor, 30)
                    )
                    
                    // Add subtle animation for status change
                    tvStatusMessage.alpha = 0f
                    tvStatusMessage.animate().alpha(1f).setDuration(500).start()
                }
                "DECLINED" -> {
                    tvStatusMessage.visibility = View.VISIBLE
                    tvStatusMessage.text = itemView.context.getString(R.string.payment_status_declined)
                    val redColor = ContextCompat.getColor(itemView.context, R.color.error)
                    tvStatusMessage.setTextColor(redColor)
                    tvStatusMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(redColor, 30)
                    )
                    
                    // Add animation
                    tvStatusMessage.alpha = 0f
                    tvStatusMessage.animate().alpha(1f).setDuration(500).start()
                    
                    if (!declineReason.isNullOrEmpty()) {
                        tvDeclineReason.visibility = View.VISIBLE
                        tvDeclineReason.text = "Reason: $declineReason"
                    }
                    
                    if (isSender) {
                        btnRequestAgain.visibility = View.VISIBLE
                        btnRequestAgain.setOnClickListener { paymentListener?.onRequestAgain(message) }
                    }
                }
            }
        }
    }

    inner class CallRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        private val btnReject: Button = itemView.findViewById(R.id.btnReject)
        private val layoutActions: LinearLayout = itemView.findViewById(R.id.layoutActions)

        fun bind(message: MessageModel) {
            tvMessage.text = message.message
            tvStatus.text = itemView.context.getString(R.string.status_format, message.proposalStatus ?: "PENDING")

            // Show actions only if PENDING and current user is the RECEIVER (CA)
            val isReceiver = message.receiverId == currentUserId
            val isPending = "PENDING" == message.proposalStatus

            if (isReceiver && isPending) {
                layoutActions.visibility = View.VISIBLE
                btnAccept.setOnClickListener {
                    callListener?.onRequestAccept(message)
                }
                btnReject.setOnClickListener {
                    callListener?.onRequestReject(message)
                }
            } else {
                layoutActions.visibility = View.GONE
            }
        }
    }

    inner class ProposalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvAdvanceAmount: TextView = itemView.findViewById(R.id.tvAdvanceAmount)
        private val tvFinalAmount: TextView = itemView.findViewById(R.id.tvFinalAmount)
        private val tvPaymentStage: TextView = itemView.findViewById(R.id.tvPaymentStage)
        private val tvRejectionReason: TextView = itemView.findViewById(R.id.tvRejectionReason)
        private val tvProposalVersion: TextView = itemView.findViewById(R.id.tvProposalVersion)
        private val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        private val btnReject: Button = itemView.findViewById(R.id.btnReject)
        private val btnRevise: Button = itemView.findViewById(R.id.btnRevise)
        private val layoutActions: LinearLayout = itemView.findViewById(R.id.layoutActions)
        private val layoutSenderActions: LinearLayout = itemView.findViewById(R.id.layoutSenderActions)

        fun bind(message: MessageModel) {
            tvDescription.text = message.proposalDescription
            tvAmount.text = itemView.context.getString(R.string.currency_format, message.proposalAmount ?: "0")

            val status = message.proposalStatus ?: "PENDING"
            tvStatus.text = status

            val version = message.proposalVersion
            if (version != null && version > 0) {
                tvProposalVersion.text = itemView.context.getString(R.string.proposal_version, version)
                tvProposalVersion.visibility = View.VISIBLE
            } else {
                tvProposalVersion.visibility = View.GONE
            }

            val rejectionReason = message.proposalRejectionReason
            if ("REJECTED" == status && !rejectionReason.isNullOrEmpty() && rejectionReason.trim().isNotEmpty()) {
                tvRejectionReason.text = itemView.context.getString(R.string.rejection_reason_format, rejectionReason.trim())
                tvRejectionReason.visibility = View.VISIBLE
            } else {
                tvRejectionReason.visibility = View.GONE
            }

            val advanceAmount = message.proposalAdvanceAmount ?: "--"
            val finalAmount = message.proposalFinalAmount ?: "--"
            tvAdvanceAmount.text = itemView.context.getString(R.string.currency_format, advanceAmount)
            tvFinalAmount.text = itemView.context.getString(R.string.currency_format, finalAmount)
            tvPaymentStage.text = getPaymentStageLabel(message, status)

            val receiverId = message.receiverId
            val senderId = message.senderId
            val isReceiver = receiverId != null && receiverId == currentUserId
            val isSender = senderId != null && senderId == currentUserId
            val isPending = "PENDING" == status
            val stage = message.proposalPaymentStage
            val advancePaid = message.proposalAdvancePaid == true
            
            val isBookingRequest = message.proposalDescription?.contains("Booking Request", ignoreCase = true) == true
            val canPayAdvance = if (isBookingRequest) {
                isSender && !advancePaid && ("ADVANCE_DUE" == stage || "ACCEPTED" == status)
            } else {
                isReceiver && !advancePaid && ("ADVANCE_DUE" == stage || "ACCEPTED" == status)
            }
            
            val canAcceptOrReject = isReceiver && isPending

            if (canAcceptOrReject) {
                layoutActions.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE
                btnReject.text = "Decline"
                btnReject.setOnClickListener {
                    proposalListener?.onReject(message) ?: run {
                        Toast.makeText(itemView.context, R.string.payment_action_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
                
                btnAccept.visibility = View.VISIBLE
                btnAccept.text = "Accept"
                btnAccept.setOnClickListener {
                    proposalListener?.onAccept(message) ?: run {
                        Toast.makeText(itemView.context, R.string.payment_action_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (canPayAdvance) {
                layoutActions.visibility = View.VISIBLE
                btnReject.visibility = View.GONE
                
                btnAccept.visibility = View.VISIBLE
                btnAccept.text = "Pay advance"
                btnAccept.setOnClickListener {
                    proposalListener?.onPayAdvance(message) ?: run {
                        Toast.makeText(itemView.context, R.string.payment_action_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                layoutActions.visibility = View.GONE
            }
            if (isSender && "REJECTED" == status) {
                layoutSenderActions.visibility = View.VISIBLE
                btnRevise.setOnClickListener {
                    proposalListener?.onRevise(message)
                }
            } else {
                layoutSenderActions.visibility = View.GONE
            }
        }

        private fun getPaymentStageLabel(message: MessageModel, status: String): String {
            val stage = message.proposalPaymentStage
            if (message.proposalFinalPaid == true || "FINAL_PAID" == stage) {
                return itemView.context.getString(R.string.payment_final_paid)
            }
            if ("FINAL_DUE" == stage) {
                return itemView.context.getString(R.string.payment_final_due)
            }
            if (message.proposalAdvancePaid == true || "ADVANCE_PAID" == stage) {
                return itemView.context.getString(R.string.payment_advance_paid)
            }
            if ("ACCEPTED" == status || "ADVANCE_DUE" == stage) {
                return itemView.context.getString(R.string.payment_advance_due)
            }
            return itemView.context.getString(R.string.payment_awaiting_acceptance)
        }
    }

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvChatMessage)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvChatTime)
        private val ivReadStatus: ImageView = itemView.findViewById(R.id.ivReadStatus)
        private val pbUpload: ProgressBar? = itemView.findViewById(R.id.pbUpload)
        private val ivUploadFailed: ImageView? = itemView.findViewById(R.id.ivUploadFailed)
        private val tvRetry: TextView? = itemView.findViewById(R.id.tvRetry)
        private val ivChatPreview: ImageView = itemView.findViewById(R.id.ivChatPreview)
        private val cardPreview: View = itemView.findViewById(R.id.cardPreview)
        private val layoutDocument: LinearLayout? = itemView.findViewById(R.id.layoutDocument)
        private val tvDocName: TextView? = itemView.findViewById(R.id.tvDocName)
        private val ivDocThumbnail: ImageView? = itemView.findViewById(R.id.ivDocThumbnail)

        fun bind(message: MessageModel, callListener: OnCallActionListener?) {
            // Reset visibilities
            tvMessage.visibility = View.GONE
            cardPreview.visibility = View.GONE
            layoutDocument?.visibility = View.GONE

            // Read Status and Upload Status
            when (message.uploadStatus) {
                "UPLOADING" -> {
                    pbUpload?.visibility = View.VISIBLE
                    ivUploadFailed?.visibility = View.GONE
                    tvRetry?.visibility = View.GONE
                    ivReadStatus.visibility = View.GONE
                }
                "FAILED" -> {
                    pbUpload?.visibility = View.GONE
                    ivUploadFailed?.visibility = View.VISIBLE
                    tvRetry?.visibility = View.VISIBLE
                    ivReadStatus.visibility = View.GONE
                    
                    // Retry upload logic
                    tvRetry?.setOnClickListener {
                        retryUploadListener?.onRetry(message)
                    }
                }
                else -> {
                    pbUpload?.visibility = View.GONE
                    ivUploadFailed?.visibility = View.GONE
                    tvRetry?.visibility = View.GONE
                    ivReadStatus.visibility = View.VISIBLE
                    
                    if (message.isRead) {
                        ivReadStatus.setColorFilter(ContextCompat.getColor(itemView.context, R.color.status_online)) // Blue/Green
                    } else {
                        ivReadStatus.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_muted)) // Grey
                    }
                }
            }

            if ("CALL" == message.type || "call" == message.type) {
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = itemView.context.getString(R.string.video_call_started)
                tvMessage.typeface = Typeface.DEFAULT_BOLD
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.on_primary))
                tvMessage.setOnClickListener {
                     callListener?.onJoinCall(message)
                }
            } else if ("DOCUMENT" == message.type) {
                if (layoutDocument != null) {
                    layoutDocument.visibility = View.VISIBLE
                    var docName = itemView.context.getString(R.string.document)
                    if (!message.message.isNullOrEmpty() && message.message != "Attachment") {
                        docName = message.message!!
                    }
                    tvDocName?.text = docName
                    
                    if (!message.imageUrl.isNullOrEmpty() && ivDocThumbnail != null) {
                        ivDocThumbnail.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(message.imageUrl)
                            .into(ivDocThumbnail)
                    } else {
                        ivDocThumbnail?.visibility = View.GONE
                    }
                    
                    layoutDocument.setOnClickListener {
                        // Fallback chain for backward compatibility with old messages
                        val urlToOpen = message.documentUrl
                            ?: message.imageUrl
                            ?: message.message
                        if (!urlToOpen.isNullOrBlank()) {
                            try {
                                CustomTabsIntent.Builder()
                                    .setShowTitle(true)
                                    .build()
                                    .launchUrl(itemView.context, Uri.parse(urlToOpen))
                            } catch (e: Exception) {
                                val ctx = itemView.context
                                Toast.makeText(ctx, ctx.getString(R.string.cannot_open_document), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    // Fallback if layoutDocument is missing (should not happen with updated xml)
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = itemView.context.getString(R.string.view_document)
                }
            } else if (!message.imageUrl.isNullOrEmpty()) {
                cardPreview.visibility = View.VISIBLE
                Glide.with(itemView.context)
                        .load(message.imageUrl)
                        .into(ivChatPreview)
            } else {
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = message.message
                tvMessage.paintFlags = tvMessage.paintFlags and (android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv())
                tvMessage.typeface = Typeface.DEFAULT
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.on_primary))
                tvMessage.setOnClickListener(null)
            }
            tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvChatMessage)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvChatTime)
        private val ivChatPreview: ImageView = itemView.findViewById(R.id.ivChatPreview)
        private val cardPreview: View = itemView.findViewById(R.id.cardPreview)
        private val layoutDocument: LinearLayout? = itemView.findViewById(R.id.layoutDocument)
        private val tvDocName: TextView? = itemView.findViewById(R.id.tvDocName)
        private val ivDocThumbnail: ImageView? = itemView.findViewById(R.id.ivDocThumbnail)

        fun bind(message: MessageModel, callListener: OnCallActionListener?) {
            // Reset visibilities
            tvMessage.visibility = View.GONE
            cardPreview.visibility = View.GONE
            layoutDocument?.visibility = View.GONE

            if ("CALL" == message.type || "call" == message.type) {
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = itemView.context.getString(R.string.incoming_video_call)
                tvMessage.typeface = Typeface.DEFAULT_BOLD
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark))
                tvMessage.setOnClickListener {
                    callListener?.onJoinCall(message)
                }
            } else if ("DOCUMENT" == message.type) {
                if (layoutDocument != null) {
                    layoutDocument.visibility = View.VISIBLE
                    var docName = itemView.context.getString(R.string.document)
                    if (!message.message.isNullOrEmpty() && message.message != "Attachment") {
                        docName = message.message!!
                    }
                    tvDocName?.text = docName
                    
                    if (!message.imageUrl.isNullOrEmpty() && ivDocThumbnail != null) {
                        ivDocThumbnail.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(message.imageUrl)
                            .into(ivDocThumbnail)
                    } else {
                        ivDocThumbnail?.visibility = View.GONE
                    }
                    
                    layoutDocument.setOnClickListener {
                        // Fallback chain for backward compatibility with old messages
                        val urlToOpen = message.documentUrl
                            ?: message.imageUrl
                            ?: message.message
                        if (!urlToOpen.isNullOrBlank()) {
                            try {
                                CustomTabsIntent.Builder()
                                    .setShowTitle(true)
                                    .build()
                                    .launchUrl(itemView.context, Uri.parse(urlToOpen))
                            } catch (e: Exception) {
                                val ctx = itemView.context
                                Toast.makeText(ctx, ctx.getString(R.string.cannot_open_document), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                     // Fallback if layoutDocument is missing
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = itemView.context.getString(R.string.view_document)
                }
            } else if (!message.imageUrl.isNullOrEmpty()) {
                cardPreview.visibility = View.VISIBLE
                Glide.with(itemView.context)
                        .load(message.imageUrl)
                        .into(ivChatPreview)
            } else {
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = message.message
                tvMessage.paintFlags = tvMessage.paintFlags and (android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv())
                tvMessage.typeface = Typeface.DEFAULT
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.black))
                tvMessage.setOnClickListener(null)
            }
            tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_PROPOSAL = 3
        private const val VIEW_TYPE_CALL_REQUEST = 4
        private const val VIEW_TYPE_PAYMENT_REQUEST = 5
        
        fun formatTime(timestamp: Long): String {
            val date = Date(timestamp)
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(date)
        }
    }
}
