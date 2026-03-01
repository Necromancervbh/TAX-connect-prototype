package com.example.taxconnect.features.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateAdapter(
    private val dates: List<Calendar>,
    private val listener: OnDateSelectedListener
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    private var selectedPosition = 0 // Default first day selected

    interface OnDateSelectedListener {
        fun onDateSelected(date: Calendar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false)
        return DateViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(dates[position], position)
    }

    override fun getItemCount(): Int {
        return dates.size
    }

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDay: TextView = itemView.findViewById(R.id.tvDay)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val cardDate: MaterialCardView = itemView.findViewById(R.id.cardDate)

        fun bind(date: Calendar, position: Int) {
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateFormat = SimpleDateFormat("dd", Locale.getDefault())

            tvDay.text = dayFormat.format(date.time)
            tvDate.text = dateFormat.format(date.time)

            if (selectedPosition == position) {
                cardDate.setCardBackgroundColor(itemView.context.getColor(R.color.primary))
                tvDay.setTextColor(itemView.context.getColor(android.R.color.white))
                tvDate.setTextColor(itemView.context.getColor(android.R.color.white))
                cardDate.strokeWidth = 0
            } else {
                cardDate.setCardBackgroundColor(itemView.context.getColor(android.R.color.white))
                tvDay.setTextColor(itemView.context.getColor(R.color.slate_500))
                tvDate.setTextColor(itemView.context.getColor(R.color.slate_900))
                cardDate.setStrokeColor(itemView.context.getColor(R.color.slate_200))
                cardDate.strokeWidth = 2 // 1dp approx
            }

            itemView.setOnClickListener {
                val previous = selectedPosition
                selectedPosition = position
                notifyItemChanged(previous)
                notifyItemChanged(selectedPosition)
                listener.onDateSelected(date)
            }
        }
    }
}
