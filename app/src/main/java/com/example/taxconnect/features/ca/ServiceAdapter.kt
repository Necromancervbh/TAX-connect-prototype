package com.example.taxconnect.features.ca

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.data.models.ServiceModel

class ServiceAdapter(private val onServiceClick: (ServiceModel) -> Unit) :
    RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {

    private var services: List<ServiceModel> = ArrayList()
    private var isEditable: Boolean = false
    private var onDeleteListener: ((ServiceModel) -> Unit)? = null

    fun setServices(services: List<ServiceModel>) {
        this.services = services
        notifyDataSetChanged()
    }

    fun setEditable(editable: Boolean) {
        this.isEditable = editable
        notifyDataSetChanged()
    }

    fun setOnServiceDeleteListener(listener: (ServiceModel) -> Unit) {
        this.onDeleteListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = services[position]
        holder.bind(service, isEditable, onServiceClick, onDeleteListener)
    }

    override fun getItemCount(): Int = services.size

    class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvDesc)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val btnBuy: Button = itemView.findViewById(R.id.btnBuy)

        fun bind(
            service: ServiceModel,
            isEditable: Boolean,
            onServiceClick: (ServiceModel) -> Unit,
            onDeleteListener: ((ServiceModel) -> Unit)?
        ) {
            tvTitle.text = service.title
            tvPrice.text = "₹ ${service.price}"
            tvDesc.text = service.description
            tvTime.text = "Time: ${service.estimatedTime}"

            if (isEditable) {
                btnDelete.visibility = View.VISIBLE
                btnBuy.visibility = View.GONE
                btnDelete.setOnClickListener { onDeleteListener?.invoke(service) }
                itemView.setOnClickListener { onServiceClick(service) }
            } else {
                btnDelete.visibility = View.GONE
                btnBuy.visibility = View.VISIBLE
                btnBuy.setOnClickListener { onServiceClick(service) }
                itemView.setOnClickListener(null)
            }
        }
    }
}
