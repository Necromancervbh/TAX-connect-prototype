package com.example.taxconnect.features.wallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.TransactionModel
import java.text.SimpleDateFormat
import java.util.*

import android.widget.ImageView
import com.example.taxconnect.core.utils.PaymentUtils

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var transactions: List<TransactionModel> = ArrayList()

    fun setTransactions(transactions: List<TransactionModel>) {
        this.transactions = transactions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        holder.bind(transaction)
    }

    override fun getItemCount(): Int = transactions.size

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivTransactionIcon)

        fun bind(transaction: TransactionModel) {
            tvDescription.text = transaction.description ?: "Transaction"
            
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = transaction.timestamp?.toDate()
            tvDate.text = if (date != null) sdf.format(date) else ""
            
            tvAmount.text = PaymentUtils.formatAmount(transaction.amount)
            
            tvStatus.text = transaction.status
            
            val isSuccess = transaction.status == "SUCCESS" || transaction.status == "COMPLETED"
            val isFailed = transaction.status == "FAILED" || transaction.status == "REJECTED"
            val isPending = transaction.status == "PENDING"

            val statusColor = when {
                isSuccess -> R.color.emerald_600
                isPending -> R.color.orange_500
                isFailed -> R.color.error
                else -> R.color.text_muted
            }
            tvAmount.setTextColor(ContextCompat.getColor(itemView.context, statusColor))
            
            val statusBg = when {
                isSuccess -> R.drawable.bg_status_success
                isPending -> R.drawable.bg_status_pending
                else -> R.drawable.bg_status_rejected
            }
            tvStatus.setBackgroundResource(statusBg)

            // Set icon based on description or type if available
            val desc = transaction.description?.lowercase() ?: ""
            val iconRes = when {
                desc.contains("deposit") || desc.contains("top up") -> R.drawable.ic_add_circle
                desc.contains("withdraw") -> R.drawable.ic_arrow_upward
                desc.contains("consultation") || desc.contains("service") -> R.drawable.ic_work_material3
                else -> R.drawable.ic_wallet_material3
            }
            ivIcon.setImageResource(iconRes)
        }
    }
}
