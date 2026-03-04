package com.example.taxconnect.features.booking

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.data.models.AvailabilityModel
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.databinding.ActivityAvailabilitySettingsBinding
import com.example.taxconnect.databinding.ItemAvailabilityDayBinding
import com.example.taxconnect.R
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class AvailabilitySettingsActivity : BaseActivity<ActivityAvailabilitySettingsBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityAvailabilitySettingsBinding =
        ActivityAvailabilitySettingsBinding::inflate

    private val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    private val dayDisplayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    // State for each day
    private val dayModels = mutableMapOf<String, AvailabilityModel>()

    // Binding refs for each day row (to read values on save)
    private val dayBindings = mutableMapOf<String, ItemAvailabilityDayBinding>()

    override fun initViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Pre-fill with defaults
        days.forEach { day ->
            dayModels[day] = AvailabilityModel(
                day = day,
                enabled = day !in listOf("SATURDAY", "SUNDAY"),
                startHour = 9,
                endHour = 18
            )
        }

        // Load from Firestore (if saved previously)
        val uid = FirebaseAuth.getInstance().uid ?: return
        DataRepository.getInstance().getCAAvailability(uid, object : DataRepository.DataCallback<List<AvailabilityModel>> {
            override fun onSuccess(data: List<AvailabilityModel>?) {
                data?.forEach { model -> model.day?.let { dayModels[it] = model } }
                buildDayRows()
            }

            override fun onError(error: String?) {
                buildDayRows() // Use defaults on error
            }
        })
    }

    private fun buildDayRows() {
        binding.layoutDays.removeAllViews()
        days.forEachIndexed { index, day ->
            val rowBinding = ItemAvailabilityDayBinding.inflate(layoutInflater, binding.layoutDays, true)
            dayBindings[day] = rowBinding

            val model = dayModels[day] ?: AvailabilityModel(day = day, enabled = false)
            rowBinding.tvDayName.text = dayDisplayNames[index]
            rowBinding.switchEnabled.isChecked = model.enabled

            // Update start/end button labels
            rowBinding.btnStartTime.text = formatHour(model.startHour)
            rowBinding.btnEndTime.text = formatHour(model.endHour)

            // Toggle time pickers visibility
            rowBinding.layoutTimeRange.visibility = if (model.enabled) View.VISIBLE else View.GONE
            rowBinding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                dayModels[day]?.enabled = isChecked
                rowBinding.layoutTimeRange.visibility = if (isChecked) View.VISIBLE else View.GONE
            }

            // Start time picker
            rowBinding.btnStartTime.setOnClickListener {
                val current = dayModels[day] ?: return@setOnClickListener
                TimePickerDialog(this, { _, h, _ ->
                    current.startHour = h
                    rowBinding.btnStartTime.text = formatHour(h)
                }, current.startHour, 0, false).show()
            }

            // End time picker
            rowBinding.btnEndTime.setOnClickListener {
                val current = dayModels[day] ?: return@setOnClickListener
                TimePickerDialog(this, { _, h, _ ->
                    current.endHour = h
                    rowBinding.btnEndTime.text = formatHour(h)
                }, current.endHour, 0, false).show()
            }
        }
    }

    override fun setupListeners() {
        binding.btnSaveAvailability.setOnClickListener {
            saveAll()
        }
    }

    private fun saveAll() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        binding.btnSaveAvailability.isEnabled = false
        binding.btnSaveAvailability.text = getString(R.string.saving)

        var saved = 0
        var errors = 0
        val total = dayModels.size

        dayModels.values.forEach { model ->
            DataRepository.getInstance().saveCAAvailability(uid, model, object : DataRepository.DataCallback<Void?> {
                override fun onSuccess(data: Void?) {
                    saved++
                    if (saved + errors == total) onAllDone(errors)
                }

                override fun onError(error: String?) {
                    errors++
                    if (saved + errors == total) onAllDone(errors)
                }
            })
        }
    }

    private fun onAllDone(errors: Int) {
        runOnUiThread {
            binding.btnSaveAvailability.isEnabled = true
            binding.btnSaveAvailability.text = getString(R.string.save_availability)
            if (errors == 0) {
                showToast(getString(R.string.availability_saved))
                finish()
            } else {
                showToast(getString(R.string.availability_save_partial))
            }
        }
    }

    private fun formatHour(hour: Int): String {
        val ampm = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.ENGLISH, "%02d:00 %s", h, ampm)
    }

    override fun observeViewModel() {}
}
