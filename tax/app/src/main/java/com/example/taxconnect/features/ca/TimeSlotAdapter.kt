package com.example.taxconnect.features.ca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R

class TimeSlotAdapter(private val onSlotSelected: (String) -> Unit) :
    RecyclerView.Adapter<TimeSlotAdapter.TimeSlotViewHolder>() {

    private var timeSlots: List<String> = ArrayList()
    private var selectedPosition: Int = -1

    fun setTimeSlots(slots: List<String>) {
        this.timeSlots = slots
        this.selectedPosition = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeSlotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_time_slot, parent, false)
        return TimeSlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeSlotViewHolder, position: Int) {
        val slot = timeSlots[position]
        holder.bind(slot, position == selectedPosition)
        
        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                onSlotSelected(slot)
            }
        }
    }

    override fun getItemCount(): Int = timeSlots.size

    class TimeSlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimeSlot: TextView = itemView.findViewById(R.id.tvTimeSlot)

        fun bind(slot: String, isSelected: Boolean) {
            tvTimeSlot.text = slot
            
            if (isSelected) {
                tvTimeSlot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.primary)
                tvTimeSlot.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                tvTimeSlot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.slate_100)
                tvTimeSlot.setTextColor(ContextCompat.getColor(itemView.context, R.color.slate_700))
            }
        }
    }
}
