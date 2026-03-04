package com.example.taxconnect.features.booking

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ItemBookingBinding
import com.example.taxconnect.data.models.BookingModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookingAdapter(
    private val listener: OnBookingActionListener?,
    private val isCaView: Boolean
) : RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    private var bookings: List<BookingModel> = ArrayList()

    interface OnBookingActionListener {
        fun onAccept(booking: BookingModel)
        fun onReject(booking: BookingModel)
        fun onBookingClick(booking: BookingModel)
        fun onMilestonesClick(booking: BookingModel)
        fun onAddToCalendar(booking: BookingModel)
        fun onMarkComplete(booking: BookingModel)
    }

    fun setBookings(bookings: List<BookingModel>) {
        this.bookings = bookings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(bookings[position])
    }

    override fun getItemCount(): Int {
        return bookings.size
    }

    inner class BookingViewHolder(private val binding: ItemBookingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: BookingModel) {
            if (isCaView) {
                binding.tvName.text = booking.userName ?: "User"
                binding.tvRoleLabel.text = itemView.context.getString(R.string.client_role)
                binding.ivAvatar.setImageResource(R.drawable.ic_person_material3)
            } else {
                binding.tvName.text = booking.caName ?: "CA"
                binding.tvRoleLabel.text = itemView.context.getString(R.string.chartered_accountant_role)
                binding.ivAvatar.setImageResource(R.drawable.ic_work_material3) // Differentiate CA
            }

            val sdf = SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault())
            binding.tvDateTime.text = sdf.format(Date(booking.appointmentTimestamp))

            if (!booking.message.isNullOrEmpty() && booking.message!!.trim().isNotEmpty()) {
                binding.tvMessage.text = booking.message
                binding.tvMessage.visibility = View.VISIBLE
            } else {
                binding.tvMessage.visibility = View.GONE
            }

            // Show service name
            if (!booking.serviceName.isNullOrBlank()) {
                binding.tvServiceName.text = booking.serviceName
                binding.tvServiceName.visibility = View.VISIBLE
            } else {
                binding.tvServiceName.visibility = View.GONE
            }

            val currentTime = System.currentTimeMillis()
            val isExpired = booking.appointmentTimestamp < currentTime
            var status = booking.status
            if (status.isNullOrEmpty() || status!!.trim().isEmpty()) {
                status = "PENDING"
            }

            // Show "Expired" chip only when Firestore status is explicitly EXPIRED.
            // (isExpired is still used below to hide action buttons for overdue bookings.)
            if (status == "EXPIRED") {
                binding.chipStatus.text = itemView.context.getString(R.string.status_expired)
                setChipStyle(binding.chipStatus, R.color.error, R.color.error_container)
                binding.layoutActions.visibility = View.GONE
            } else {
                when (status) {
                    "PENDING" -> {
                        binding.chipStatus.text = itemView.context.getString(R.string.booking_pending)
                        setChipStyle(binding.chipStatus, R.color.amber_700, R.color.amber_50)
                        if (isCaView) {
                            binding.layoutActions.visibility = View.VISIBLE
                        } else {
                            binding.layoutActions.visibility = View.GONE
                        }
                    }
                    "ACCEPTED" -> {
                        // Display "Upcoming" for future accepted bookings
                        binding.chipStatus.text = itemView.context.getString(R.string.status_upcoming)
                        setChipStyle(binding.chipStatus, R.color.emerald_700, R.color.emerald_50)
                        binding.layoutActions.visibility = View.GONE

                        val otherPartyName = if (isCaView) booking.userName else booking.caName
                        com.example.taxconnect.core.utils.BookingReminderScheduler.schedule(
                            itemView.context, booking, otherPartyName ?: "User"
                        )
                    }
                    "CONFIRMED" -> {
                        binding.chipStatus.text = itemView.context.getString(R.string.status_confirmed)
                        setChipStyle(binding.chipStatus, R.color.emerald_700, R.color.emerald_50)
                        binding.layoutActions.visibility = View.GONE

                        val otherPartyName = if (isCaView) booking.userName else booking.caName
                        com.example.taxconnect.core.utils.BookingReminderScheduler.schedule(
                            itemView.context, booking, otherPartyName ?: "User"
                        )
                    }
                    "REJECTED" -> {
                        binding.chipStatus.text = itemView.context.getString(R.string.status_declined)
                        setChipStyle(binding.chipStatus, R.color.error, R.color.error_container)
                        binding.layoutActions.visibility = View.GONE
                        
                        // Show rejection reason if provided (visible to the user/client)
                        if (!isCaView && !booking.rejectionReason.isNullOrBlank()) {
                            binding.llRejectionReason.visibility = View.VISIBLE
                            binding.tvRejectionReason.text = "Reason: ${booking.rejectionReason}"
                        } else {
                            binding.llRejectionReason.visibility = View.GONE
                        }

                        booking.id?.let {
                            com.example.taxconnect.core.utils.BookingReminderScheduler.cancel(itemView.context, it)
                        }
                    }
                    "COMPLETED" -> {
                        binding.chipStatus.text = itemView.context.getString(R.string.status_completed)
                        setChipStyle(binding.chipStatus, R.color.primary, R.color.primary_container)
                        binding.layoutActions.visibility = View.GONE
                        binding.layoutTimeline.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.chipStatus.text = status
                        binding.layoutActions.visibility = View.GONE
                        setChipStyle(binding.chipStatus, R.color.text_secondary, R.color.surface_variant) // Default fallback
                    }
                }
            }

            if (status != "REJECTED") {
                binding.llRejectionReason.visibility = View.GONE
            }
            if (status != "COMPLETED") {
                binding.layoutTimeline.visibility = View.GONE
            }

            binding.btnAccept.setOnClickListener {
                listener?.onAccept(booking)
            }

            binding.btnReject.setOnClickListener {
                listener?.onReject(booking)
            }

            if ("ACCEPTED" == status || "COMPLETED" == status || "CONFIRMED" == status) {
                binding.btnMilestones.visibility = View.VISIBLE
                binding.btnMilestones.setOnClickListener {
                    listener?.onMilestonesClick(booking)
                }
            } else {
                binding.btnMilestones.visibility = View.GONE
            }

            // Show calendar button for upcoming/confirmed bookings
            if (("ACCEPTED" == status || "CONFIRMED" == status) && !isExpired) {
                binding.btnAddToCalendar.visibility = View.VISIBLE
                binding.btnAddToCalendar.setOnClickListener {
                    listener?.onAddToCalendar(booking)
                }
            } else {
                binding.btnAddToCalendar.visibility = View.GONE
            }


            // Mark as Complete — visible for CA on ACCEPTED bookings
            if (isCaView && (status == "ACCEPTED" || status == "CONFIRMED") && !isExpired) {
                binding.btnMarkComplete.visibility = View.VISIBLE
                binding.btnMarkComplete.setOnClickListener { listener?.onMarkComplete(booking) }
            } else {
                binding.btnMarkComplete.visibility = View.GONE
            }
            binding.root.setOnClickListener {
                listener?.onBookingClick(booking)
            }
        }

        private fun setChipStyle(chip: com.google.android.material.chip.Chip, textColorRes: Int, bgColorRes: Int) {
            chip.setTextColor(ContextCompat.getColor(itemView.context, textColorRes))
            chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, bgColorRes))
        }
    }
}
