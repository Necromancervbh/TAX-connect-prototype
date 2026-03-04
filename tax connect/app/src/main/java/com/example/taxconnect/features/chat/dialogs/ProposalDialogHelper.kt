package com.example.taxconnect.features.chat.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.R
import com.example.taxconnect.data.models.MessageModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class ProposalDialogHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val onSendProposal: (MessageModel) -> Unit,
    private val onSendPaymentRequest: (MessageModel) -> Unit
) {

    fun showCreateProposalDialog(
        currentUserId: String?,
        otherUserId: String?,
        currentChatId: String?
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_proposal, null)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etAdvanceAmount = dialogView.findViewById<TextInputEditText>(R.id.etAdvanceAmount)
        val btnSendProposal = dialogView.findViewById<View>(R.id.btnSendProposal)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()
        dialog.show()

        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnSendProposal?.setOnClickListener {
            val description = etDescription?.text.toString().trim()
            val totalAmountStr = etAmount?.text.toString().trim()
            val advanceAmountStr = etAdvanceAmount?.text.toString().trim()

            if (description.isEmpty() || totalAmountStr.isEmpty()) {
                if (context is BaseActivity<*>) {
                    context.showToast(context.getString(R.string.fill_details_error))
                } else {
                    Toast.makeText(context, context.getString(R.string.fill_details_error), Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            val totalAmount = totalAmountStr.toDoubleOrNull() ?: 0.0
            val advanceAmount = if (advanceAmountStr.isNotEmpty()) advanceAmountStr.toDoubleOrNull() ?: 0.0 else 0.0
            
            if (advanceAmount > totalAmount) {
                if (context is BaseActivity<*>) {
                    context.showToast("Advance cannot exceed total amount")
                } else {
                    Toast.makeText(context, "Advance cannot exceed total amount", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            val finalAmount = totalAmount - advanceAmount

            val message = MessageModel(
                senderId = currentUserId,
                receiverId = otherUserId,
                chatId = currentChatId,
                message = "📄 Sent a proposal: $description",
                timestamp = System.currentTimeMillis(),
                type = "PROPOSAL",
                proposalDescription = description,
                proposalAmount = totalAmountStr,
                proposalStatus = "PENDING"
            )
            
            if (advanceAmount > 0) {
                message.proposalAdvanceAmount = advanceAmountStr
                message.proposalFinalAmount = finalAmount.toString()
                message.proposalPaymentStage = "ADVANCE_DUE"
                message.proposalAdvancePaid = false
                message.proposalFinalPaid = false
            } else {
                message.proposalAdvanceAmount = "0"
                message.proposalFinalAmount = totalAmountStr
                message.proposalPaymentStage = "FINAL_DUE"
                message.proposalAdvancePaid = true
                message.proposalFinalPaid = false
            }

            onSendProposal(message)
            dialog.dismiss()
        }
    }

    fun showPaymentRequestDialog(
        currentUserId: String?,
        otherUserId: String?,
        currentChatId: String?
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_request_payment, null)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etPaymentDescription)
        val etPaymentAmount = dialogView.findViewById<TextInputEditText>(R.id.etPaymentAmount)
        val togglePaymentType = dialogView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.togglePaymentType)
        val btnSend = dialogView.findViewById<View>(R.id.btnSendRequest)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        btnSend?.setOnClickListener {
            val description = etDescription?.text?.toString()?.trim() ?: ""
            val amountStr = etPaymentAmount?.text?.toString()?.trim() ?: ""
            
            if (description.isEmpty()) {
                etDescription?.error = "Description required"
                return@setOnClickListener
            }
            if (amountStr.isEmpty()) {
                etPaymentAmount?.error = "Amount required"
                return@setOnClickListener
            }

            val paymentType = if (togglePaymentType?.checkedButtonId == R.id.btnAdvance) {
                "ADVANCE"
            } else {
                "FINAL"
            }

            val message = createPaymentRequestMessage(
                currentUserId, otherUserId, currentChatId, description, amountStr, paymentType
            )
            onSendPaymentRequest(message)
            
            dialog.dismiss()
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    
    private fun createPaymentRequestMessage(
        currentUserId: String?,
        otherUserId: String?,
        currentChatId: String?,
        description: String,
        amount: String,
        stage: String
    ): MessageModel {
        val message = MessageModel(
            senderId = currentUserId,
            receiverId = otherUserId,
            chatId = currentChatId,
            message = description,
            timestamp = System.currentTimeMillis(),
            type = "PAYMENT_REQUEST"
        )
        message.paymentRequestAmount = amount
        message.paymentRequestStatus = "PENDING"
        message.paymentStage = stage
        return message
    }
}
