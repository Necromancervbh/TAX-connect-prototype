package com.example.taxconnect.features.booking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.core.utils.SlotGenerator
import com.example.taxconnect.data.models.AvailabilityModel
import com.example.taxconnect.data.models.ServiceModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.databinding.ActivityBookAppointmentBinding
import com.example.taxconnect.databinding.ItemDateStripBinding
import com.example.taxconnect.databinding.ItemTimeSlotChipBinding
import com.example.taxconnect.databinding.ItemServiceSelectionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BookAppointmentActivity : BaseActivity<ActivityBookAppointmentBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityBookAppointmentBinding = ActivityBookAppointmentBinding::inflate

    private var ca: UserModel? = null
    private var currentUserId: String? = null
    private var selectedService: ServiceModel? = null
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedTimeSlot: String? = null

    private var currentStep = 1

    // CA availability loaded from Firestore; null = not yet loaded
    private var caAvailabilityMap: Map<String, AvailabilityModel>? = null

    // 14 days of dates for the strip
    private val dateList: List<Calendar> by lazy {
        val list = mutableListOf<Calendar>()
        val base = Calendar.getInstance()
        repeat(14) { i ->
            val c = Calendar.getInstance()
            c.timeInMillis = base.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, i)
            list.add(c)
        }
        list
    }

    override fun initViews() {
        ca = intent.getSerializableExtra("CA_DATA") as? UserModel
        currentUserId = FirebaseAuth.getInstance().uid

        if (ca == null || currentUserId == null) {
            finish()
            return
        }

        setupToolbar()
        setupStepper()
        loadServices()
        loadCAAvailability()
        setupDateStrip()
        setupQuickDateChips()
        updateUIForStep()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
    }

    private fun handleBackPress() {
        if (currentStep == 3) {
            // Back-guard on confirmation step
            MaterialAlertDialogBuilder(this)
                .setTitle("Leave booking?")
                .setMessage("Your date and time selection will be lost.")
                .setPositiveButton("Leave") { _, _ ->
                    currentStep--
                    updateUIForStep()
                }
                .setNegativeButton("Stay") { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentStep > 1) {
            currentStep--
            updateUIForStep()
        } else {
            finish()
        }
    }

    override fun setupListeners() {
        binding.btnNext.setOnClickListener {
            handleNextClick()
        }
        binding.btnBack.setOnClickListener {
            handleBackPress()
        }
        binding.btnChatNoServices.setOnClickListener {
            val targetId = ca?.uid ?: return@setOnClickListener
            val intent = Intent(this, com.example.taxconnect.features.chat.ChatActivity::class.java)
            intent.putExtra("otherUserId", targetId)
            intent.putExtra("otherUserName", ca?.name)
            startActivity(intent)
        }
    }

    private fun handleNextClick() {
        when (currentStep) {
            1 -> {
                if (selectedService == null) {
                    showToast("Please select a service")
                    return
                }
                currentStep = 2
                refreshTimeSlots()
                updateUIForStep()
            }
            2 -> {
                if (selectedTimeSlot == null) {
                    showToast("Please select a time slot")
                    return
                }
                currentStep = 3
                prepareSummary()
                updateUIForStep()
            }
            3 -> {
                processBooking()
            }
        }
    }

    private fun updateUIForStep() {
        binding.viewFlipper.displayedChild = currentStep - 1
        binding.btnBack.visibility = if (currentStep > 1) View.VISIBLE else View.INVISIBLE
        binding.btnNext.text = if (currentStep == 3) "Confirm Booking" else "Next"
        updateStepperView()
    }

    private fun updateStepperView() {
        val colorPrimary = com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary)
        val colorOutline = com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(this, androidx.appcompat.R.attr.colorControlNormal)

        fun updateStep(ivId: Int, tvNumId: Int, tvTitleId: Int, step: Int) {
            val iv = binding.root.findViewById<android.widget.ImageView>(ivId) ?: return
            val tvNum = binding.root.findViewById<android.widget.TextView>(tvNumId) ?: return
            val tvTitle = binding.root.findViewById<android.widget.TextView>(tvTitleId) ?: return
            if (currentStep > step) {
                iv.visibility = View.VISIBLE
                tvNum.visibility = View.GONE
                (iv.parent as View).setBackgroundResource(R.drawable.bg_circle_primary)
                tvTitle.setTextColor(colorPrimary)
            } else if (currentStep == step) {
                iv.visibility = View.GONE
                tvNum.visibility = View.VISIBLE
                (iv.parent as View).setBackgroundResource(R.drawable.bg_circle_primary)
                tvTitle.setTextColor(colorPrimary)
            } else {
                iv.visibility = View.GONE
                tvNum.visibility = View.VISIBLE
                (iv.parent as View).setBackgroundResource(R.drawable.bg_circle_outline)
                tvTitle.setTextColor(colorOutline)
            }
        }

        updateStep(R.id.ivStep1, R.id.tvStep1Num, R.id.tvStep1Title, 1)
        updateStep(R.id.ivStep2, R.id.tvStep2Num, R.id.tvStep2Title, 2)
        updateStep(R.id.ivStep3, R.id.tvStep3Num, R.id.tvStep3Title, 3)
    }

    private fun setupStepper() {
        // Initial update handled by updateUIForStep
    }

    private fun loadServices() {
        binding.progressBar.visibility = View.VISIBLE
        val uid = ca?.uid ?: return

        DataRepository.getInstance().getServices(uid, object : DataRepository.DataCallback<List<ServiceModel>> {
            override fun onSuccess(data: List<ServiceModel>?) {
                binding.progressBar.visibility = View.GONE
                if (!data.isNullOrEmpty()) {
                    binding.rvServices.visibility = View.VISIBLE
                    binding.layoutNoServices.visibility = View.GONE
                    setupServiceAdapter(data)
                } else {
                    binding.rvServices.visibility = View.GONE
                    binding.layoutNoServices.visibility = View.VISIBLE
                }
            }

            override fun onError(error: String?) {
                binding.progressBar.visibility = View.GONE
                showToast("Failed to load services")
            }
        })
    }

    private fun setupServiceAdapter(services: List<ServiceModel>) {
        val adapter = ServiceSelectionAdapter(services, selectedService) { service ->
            selectedService = service
            (binding.rvServices.adapter as? ServiceSelectionAdapter)?.setSelected(service)
        }
        binding.rvServices.layoutManager = LinearLayoutManager(this)
        binding.rvServices.adapter = adapter
    }

    // ─── Date Strip ───────────────────────────────────────────────────────────

    private fun setupDateStrip() {
        val adapter = DateStripAdapter(dateList, selectedDate) { cal ->
            selectedDate = cal
            // Deselect quick chips
            binding.chipToday.isChecked = false
            binding.chipTomorrow.isChecked = false
            // Reset slot selection and refresh
            selectedTimeSlot = null
            refreshTimeSlots()
        }
        binding.rvDateStrip.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDateStrip.adapter = adapter
    }

    private fun setupQuickDateChips() {
        binding.chipToday.setOnClickListener {
            val today = Calendar.getInstance()
            selectDate(today)
            binding.chipTomorrow.isChecked = false
        }
        binding.chipTomorrow.setOnClickListener {
            val tomorrow = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, 1) }
            selectDate(tomorrow)
            binding.chipToday.isChecked = false
        }
    }

    private fun selectDate(cal: Calendar) {
        selectedDate = cal
        selectedTimeSlot = null
        (binding.rvDateStrip.adapter as? DateStripAdapter)?.setSelected(cal)
        refreshTimeSlots()
    }

    // ─── CA Availability ──────────────────────────────────────────────────────

    private fun loadCAAvailability() {
        val uid = ca?.uid ?: return
        DataRepository.getInstance().getCAAvailability(uid, object : DataRepository.DataCallback<List<AvailabilityModel>> {
            override fun onSuccess(data: List<AvailabilityModel>?) {
                caAvailabilityMap = data?.associateBy { it.day ?: "" } ?: emptyMap()
                // Re-render slots for current selection if on step 2
                if (currentStep == 2) refreshTimeSlots()
            }
            override fun onError(error: String?) {
                caAvailabilityMap = emptyMap() // Triggers default fallback
            }
        })
    }

    // ─── Time Slots ───────────────────────────────────────────────────────────

    private fun refreshTimeSlots() {
        val now = Calendar.getInstance()
        val dayKey = SlotGenerator.dayKey(selectedDate)
        val availMap = caAvailabilityMap

        val morningFiltered: List<String>
        val afternoonFiltered: List<String>

        if (availMap != null && availMap.isNotEmpty()) {
            // Use real CA availability
            val model = availMap[dayKey]
            if (model == null || !model.enabled) {
                // CA is unavailable on this day
                morningFiltered = emptyList()
                afternoonFiltered = emptyList()
            } else {
                val all = SlotGenerator.generate(model, selectedDate, now)
                morningFiltered = all.filter { !it.endsWith("PM") || it.startsWith("12") }
                afternoonFiltered = all.filter { it.endsWith("PM") && !it.startsWith("12") }
            }
        } else {
            // Fallback: CA hasn't configured availability yet
            val (morning, afternoon) = SlotGenerator.generateDefault(selectedDate, now)
            morningFiltered = morning
            afternoonFiltered = afternoon
        }

        // Morning
        val morningAdapter = TimeSlotAdapter(morningFiltered, selectedTimeSlot) { slot ->
            selectedTimeSlot = slot
            (binding.rvMorningSlots.adapter as? TimeSlotAdapter)?.setSelected(slot)
            (binding.rvAfternoonSlots.adapter as? TimeSlotAdapter)?.setSelected(null)
        }
        binding.rvMorningSlots.adapter = morningAdapter
        binding.tvMorningSlotCount.text = if (morningFiltered.isEmpty()) "No slots" else "${morningFiltered.size} available"

        // Afternoon
        val afternoonAdapter = TimeSlotAdapter(afternoonFiltered, selectedTimeSlot) { slot ->
            selectedTimeSlot = slot
            (binding.rvAfternoonSlots.adapter as? TimeSlotAdapter)?.setSelected(slot)
            (binding.rvMorningSlots.adapter as? TimeSlotAdapter)?.setSelected(null)
        }
        binding.rvAfternoonSlots.adapter = afternoonAdapter
        binding.tvAfternoonSlotCount.text = if (afternoonFiltered.isEmpty()) "No slots" else "${afternoonFiltered.size} available"
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    // ─── Summary / Booking ───────────────────────────────────────────────────

    private fun prepareSummary() {
        ca?.let {
            Glide.with(this).load(it.profileImageUrl).circleCrop().into(binding.ivExpertProfile)
            binding.tvExpertName.text = it.name
            binding.tvExpertTitle.text = it.specialization
        }
        binding.tvSelectedService.text = selectedService?.title

        val dateFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        val dateStr = dateFormat.format(selectedDate.time)
        binding.tvSelectedDateTime.text = "$dateStr • $selectedTimeSlot"

        val price = selectedService?.price ?: "0"
        binding.tvFee.text = "₹ $price"
        binding.tvFeeContext.text = "Advance payment due on acceptance"
    }

    private fun processBooking() {
        binding.btnNext.isEnabled = false
        binding.btnNext.text = "Processing..."
        binding.btnBack.isEnabled = false

        val note = binding.etNote.text.toString().trim()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(selectedDate.time)
        val displayDateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val displayDate = displayDateFormat.format(selectedDate.time)

        val uid = currentUserId ?: return
        val targetId = ca?.uid ?: return
        val svc = selectedService ?: return

        // Build a proper BookingModel (no conversation yet — CA must accept first)
        val bookingId = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("bookings").document().id

        // Parse selected time slot (e.g. "02:00 PM") into actual epoch ms for the chosen day.
        // Using selectedDate.timeInMillis would be midnight — making every booking look expired.
        val slotCal = selectedDate.clone() as Calendar
        try {
            val slotSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val slotParsed = slotSdf.parse(selectedTimeSlot ?: "")
            if (slotParsed != null) {
                val tmp = Calendar.getInstance().apply { time = slotParsed }
                slotCal.set(Calendar.HOUR_OF_DAY, tmp.get(Calendar.HOUR_OF_DAY))
                slotCal.set(Calendar.MINUTE, tmp.get(Calendar.MINUTE))
                slotCal.set(Calendar.SECOND, 0)
                slotCal.set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("BookAppointment", "Could not parse time slot '$selectedTimeSlot': ${e.message}")
        }

        val booking = com.example.taxconnect.data.models.BookingModel(
            id = bookingId,
            caId = targetId,
            userId = uid,
            userName = null,         // not critical — populated when CA views it
            caName = ca?.name,
            serviceId = svc.id,
            serviceName = svc.title,
            appointmentTimestamp = slotCal.timeInMillis, // ← actual slot time, not midnight
            appointmentDate = displayDate,
            appointmentTime = selectedTimeSlot,
            status = "PENDING",
            message = note.ifBlank { null }
        )

        lifecycleScope.launch {
            try {
                com.example.taxconnect.data.repositories.BookingRepository().saveBooking(booking)
                showBookingSuccessDialog()
            } catch (e: Exception) {
                binding.btnNext.isEnabled = true
                binding.btnNext.text = "Confirm Booking"
                binding.btnBack.isEnabled = true
                showToast("Failed to process booking: ${e.message}")
            }
        }
    }

    private fun showBookingSuccessDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Booking Sent! ✅")
            .setMessage("Your booking request for ${selectedService?.title} has been sent to ${ca?.name}.\n\nYou'll be notified once they accept — then your chat will open automatically.")
            .setPositiveButton("View My Bookings") { _, _ ->
                val intent = android.content.Intent(this, com.example.taxconnect.features.booking.MyBookingsActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Go to Home") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun observeViewModel() {}

    // ─── Internal Adapters ────────────────────────────────────────────────────

    inner class ServiceSelectionAdapter(
        private val services: List<ServiceModel>,
        private var selected: ServiceModel?,
        private val listener: (ServiceModel) -> Unit
    ) : RecyclerView.Adapter<ServiceSelectionAdapter.ViewHolder>() {

        fun setSelected(service: ServiceModel) {
            selected = service
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemServiceSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val service = services[position]
            holder.binding.tvServiceTitle.text = service.title
            holder.binding.tvServicePrice.text = "₹ ${service.price}"
            holder.binding.tvServiceDuration.text = service.estimatedTime

            // Show description if available
            if (!service.description.isNullOrBlank()) {
                holder.binding.tvServiceDescription.text = service.description
                holder.binding.tvServiceDescription.visibility = View.VISIBLE
            } else {
                holder.binding.tvServiceDescription.visibility = View.GONE
            }

            val isSelected = service.id == selected?.id
            holder.binding.rbSelected.isChecked = isSelected
            holder.binding.root.isChecked = isSelected
            holder.binding.root.strokeWidth = if (isSelected) 3 else 1
            holder.binding.root.strokeColor = if (isSelected)
                com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(holder.itemView.context, androidx.appcompat.R.attr.colorPrimary)
            else
                holder.itemView.context.getColor(R.color.stroke)

            holder.itemView.setOnClickListener { listener(service) }
            holder.binding.rbSelected.setOnClickListener { listener(service) }
        }

        override fun getItemCount() = services.size
        inner class ViewHolder(val binding: ItemServiceSelectionBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class DateStripAdapter(
        private val dates: List<Calendar>,
        private var selected: Calendar,
        private val listener: (Calendar) -> Unit
    ) : RecyclerView.Adapter<DateStripAdapter.ViewHolder>() {

        private val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        private val dateFmt = SimpleDateFormat("d", Locale.getDefault())

        fun setSelected(cal: Calendar) {
            selected = cal
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDateStripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cal = dates[position]
            holder.binding.tvDay.text = dayFmt.format(cal.time).uppercase()
            holder.binding.tvDate.text = dateFmt.format(cal.time)

            val isSelected = isSameDay(cal, selected)
            val ctx = holder.itemView.context

            if (isSelected) {
                holder.binding.dateCard.setCardBackgroundColor(
                    com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(ctx, androidx.appcompat.R.attr.colorPrimary)
                )
                holder.binding.dateCard.strokeColor = 0
                holder.binding.tvDay.setTextColor(
                    com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(ctx, com.google.android.material.R.attr.colorOnPrimary)
                )
                holder.binding.tvDate.setTextColor(
                    com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(ctx, com.google.android.material.R.attr.colorOnPrimary)
                )
            } else {
                holder.binding.dateCard.setCardBackgroundColor(ctx.getColor(R.color.surface))
                holder.binding.dateCard.strokeColor = ctx.getColor(R.color.stroke)
                holder.binding.tvDay.setTextColor(
                    com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.binding.tvDate.setTextColor(
                    com.example.taxconnect.core.ui.ThemeHelper.getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface)
                )
            }

            holder.itemView.setOnClickListener { listener(cal) }
        }

        override fun getItemCount() = dates.size
        inner class ViewHolder(val binding: ItemDateStripBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class TimeSlotAdapter(
        private val slots: List<String>,
        private var selected: String?,
        private val listener: (String) -> Unit
    ) : RecyclerView.Adapter<TimeSlotAdapter.ViewHolder>() {

        fun setSelected(slot: String?) {
            selected = slot
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTimeSlotChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val slot = slots[position]
            holder.binding.chipTimeSlot.text = slot
            holder.binding.chipTimeSlot.isChecked = slot == selected
            holder.binding.chipTimeSlot.setOnClickListener { listener(slot) }
        }

        override fun getItemCount() = slots.size
        inner class ViewHolder(val binding: ItemTimeSlotChipBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
