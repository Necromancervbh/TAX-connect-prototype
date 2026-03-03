package com.example.taxconnect.features.milestones

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ItemMilestoneBinding
import com.example.taxconnect.data.models.MilestoneModel
import java.text.SimpleDateFormat
import java.util.Locale

class MilestoneAdapter(
    private val isCa: Boolean,
    private val onStatusClick: (MilestoneModel) -> Unit
) : RecyclerView.Adapter<MilestoneAdapter.MilestoneViewHolder>() {

    private val milestones = ArrayList<MilestoneModel>()

    fun setMilestones(list: List<MilestoneModel>?) {
        milestones.clear()
        if (list != null) {
            milestones.addAll(list)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MilestoneViewHolder {
        val binding = ItemMilestoneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MilestoneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        holder.bind(milestones[position])
    }

    override fun getItemCount(): Int = milestones.size

    inner class MilestoneViewHolder(private val binding: ItemMilestoneBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(milestone: MilestoneModel) {
            binding.tvTitle.text = milestone.title
            binding.tvDescription.text = milestone.description
            
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = milestone.timestamp?.toDate()
            binding.tvDate.text = if (date != null) sdf.format(date) else ""

            binding.tvStatus.text = milestone.status
            binding.cbStatus.isChecked = milestone.status == "COMPLETED"
            
            val context = binding.root.context
            when (milestone.status) {
                "COMPLETED" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_tag_green_light)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.green))
                }
                "IN_PROGRESS" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_tag_orange_light)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.orange))
                }
                else -> { // PENDING
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_tag_light)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.grey))
                }
            }

            if (isCa) {
                binding.root.isEnabled = true
                binding.root.setOnClickListener { onStatusClick(milestone) }
            } else {
                binding.root.isEnabled = false
                binding.root.setOnClickListener(null)
            }
        }
    }
}
