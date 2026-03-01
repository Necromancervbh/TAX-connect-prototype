package com.example.taxconnect.features.chat.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.taxconnect.R
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.core.utils.PaymentUtils
import com.example.taxconnect.core.base.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class PaymentDialogHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val onPayWithWallet: (Double, MessageModel, String) -> Unit,
    private val onPayWithGateway: (Double, MessageModel) -> Unit,
    private val onDeclinePayment: (MessageModel, String) -> Unit
) {

    fun showPaymentOptions(message: MessageModel) {
        val dialog = BottomSheetDialog(context)
        val view = layoutInflater.inflate(R.layout.layout_payment_method_options, null)
        dialog.setContentView(view)

        val btnPayWallet = view.findViewById<View>(R.id.btnPayWallet)
        val btnPayGateway = view.findViewById<View>(R.id.btnPayGateway)
        val tvAmount = view.findViewById<TextView>(R.id.tvPaymentAmount)

        val amount = when {
            message.type == "PROPOSAL" -> {
                if ("ADVANCE_DUE" == message.proposalPaymentStage) {
                    message.proposalAdvanceAmount?.toDoubleOrNull() ?: 0.0
                } else {
                    message.proposalFinalAmount?.toDoubleOrNull() ?: 0.0
                }
            }
            else -> message.paymentRequestAmount?.toDoubleOrNull() ?: 0.0
        }

        if (amount <= 0) {
            if (context is BaseActivity<*>) {
                context.showToast("Invalid payment amount")
            } else {
                Toast.makeText(context, "Invalid payment amount", Toast.LENGTH_SHORT).show()
            }
            return
        }

        tvAmount?.text = "Amount: ${PaymentUtils.formatAmount(amount)}"

        btnPayWallet.setOnClickListener {
            dialog.dismiss()
            val status = when {
                message.type == "PROPOSAL" -> {
                    if ("ADVANCE_DUE" == message.proposalPaymentStage) "ADVANCE_PAID" else "FINAL_PAID"
                }
                message.type == "PAYMENT_REQUEST" -> {
                    if ("ADVANCE" == message.paymentStage) "ADVANCE_PAID" else "FINAL_PAID"
                }
                else -> "PAID"
            }
            onPayWithWallet(amount, message, status)
        }

        btnPayGateway.setOnClickListener {
            dialog.dismiss()
            onPayWithGateway(amount, message)
        }

        dialog.show()
    }

    fun showDeclinePaymentDialog(message: MessageModel) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_decline_payment, null)
        val etReason = dialogView.findViewById<TextInputEditText>(R.id.etDeclineReason)
        val btnSubmit = dialogView.findViewById<View>(R.id.btnSubmitDecline)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        btnSubmit?.setOnClickListener {
            val reason = etReason?.text.toString().trim()
            if (reason.isEmpty()) {
                etReason?.error = "Reason is required"
                return@setOnClickListener
            }
            
            onDeclinePayment(message, reason)
            dialog.dismiss()
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
