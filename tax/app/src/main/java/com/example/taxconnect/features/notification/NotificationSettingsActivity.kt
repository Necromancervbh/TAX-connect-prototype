package com.example.taxconnect.features.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivityNotificationSettingsBinding
import com.example.taxconnect.core.utils.SecurityUtils
import android.app.TimePickerDialog
import androidx.core.app.NotificationManagerCompat
import com.example.taxconnect.core.utils.NotificationHelper
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build

import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.core.ui.ThemeHelper
import com.example.taxconnect.core.base.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.taxconnect.databinding.BottomSheetThemeSelectionBinding

class NotificationSettingsActivity : BaseActivity<ActivityNotificationSettingsBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityNotificationSettingsBinding = ActivityNotificationSettingsBinding::inflate

    private var startTime: String = "22:00"
    private var endTime: String = "07:00"

    override fun initViews() {
        setupUI()
        
        if (intent.getBooleanExtra("open_themes", false)) {
            showThemeSelection()
        }
    }

    override fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSelectTheme.setOnClickListener {
            showThemeSelection()
        }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            ThemeHelper.setBiometricEnabled(this, isChecked)
            if (isChecked) {
                showToast("Biometric lock enabled")
            } else {
                showToast("Biometric lock disabled")
            }
        }

        binding.switchQuietHours.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutQuietHoursTimes.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnStartTime.setOnClickListener {
            showTimePicker(startTime) { time ->
                startTime = time
                binding.tvStartTime.text = time
            }
        }

        binding.btnEndTime.setOnClickListener {
            showTimePicker(endTime) { time ->
                endTime = time
                binding.tvEndTime.text = time
            }
        }

        binding.btnPreviewNotification.setOnClickListener {
            requestNotificationPermission()
        }

        // Battery Optimization
        updateBatteryStatus()
        binding.btnOptimizeBattery.setOnClickListener {
            NotificationHelper.requestIgnoreBatteryOptimizations(this)
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }

        binding.btnConfigMessages.setOnClickListener { openChannelSettings(NotificationHelper.CHANNEL_MESSAGES) }
        binding.btnConfigCalls.setOnClickListener { openChannelSettings(NotificationHelper.CHANNEL_CALLS) }
        binding.btnConfigRequests.setOnClickListener { openChannelSettings(NotificationHelper.CHANNEL_REQUESTS) }
        binding.btnConfigBookings.setOnClickListener { openChannelSettings(NotificationHelper.CHANNEL_BOOKINGS) }

        binding.btnSave.setOnClickListener {
            val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
            
            // Parse startTime and endTime to components for MyFirebaseMessagingService
            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 22
            val startMin = startParts.getOrNull(1)?.toIntOrNull() ?: 0
            val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 7
            val endMin = endParts.getOrNull(1)?.toIntOrNull() ?: 0

            prefs.edit().apply {
                putBoolean("notify_messages", binding.switchMessages.isChecked)
                putBoolean("notify_calls", binding.switchCalls.isChecked)
                putBoolean("notify_requests", binding.switchRequests.isChecked)
                putBoolean("notify_bookings", binding.switchBookings.isChecked)
                putBoolean("quiet_hours_enabled", binding.switchQuietHours.isChecked)
                putString("quiet_hours_start", startTime)
                putString("quiet_hours_end", endTime)
                putInt("quiet_hours_start_hour", startHour)
                putInt("quiet_hours_start_min", startMin)
                putInt("quiet_hours_end_hour", endHour)
                putInt("quiet_hours_end_min", endMin)
                apply()
            }

            // Sync with Firestore
            val updates = mapOf(
                "notifyMessages" to binding.switchMessages.isChecked,
                "notifyCalls" to binding.switchCalls.isChecked,
                "notifyRequests" to binding.switchRequests.isChecked,
                "notifyBookings" to binding.switchBookings.isChecked,
                "quietHoursEnabled" to binding.switchQuietHours.isChecked,
                "quietHoursStart" to startTime,
                "quietHoursEnd" to endTime
            )
            
            UserRepository.getInstance().updateNotificationPreferences(updates) { success, error ->
                if (success) {
                    showToast(getString(R.string.notification_prefs_saved))
                } else {
                    showToast("Error syncing preferences: $error")
                }
                finish()
            }
        }
    }

    override fun observeViewModel() {
        // No ViewModel to observe yet
    }

    private fun setupUI() {
        val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        binding.switchMessages.isChecked = prefs.getBoolean("notify_messages", true)
        binding.switchCalls.isChecked = prefs.getBoolean("notify_calls", true)
        binding.switchRequests.isChecked = prefs.getBoolean("notify_requests", true)
        binding.switchBookings.isChecked = prefs.getBoolean("notify_bookings", true)

        binding.switchBiometric.isChecked = ThemeHelper.isBiometricEnabled(this)

        val quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", false)
        binding.switchQuietHours.isChecked = quietHoursEnabled
        binding.layoutQuietHoursTimes.visibility = if (quietHoursEnabled) View.VISIBLE else View.GONE

        startTime = prefs.getString("quiet_hours_start", "22:00") ?: "22:00"
        endTime = prefs.getString("quiet_hours_end", "07:00") ?: "07:00"
        binding.tvStartTime.text = startTime
        binding.tvEndTime.text = endTime

        updateThemeText()
    }

    private fun updateThemeText() {
        val currentTheme = ThemeHelper.getSelectedTheme(this)
        binding.tvCurrentTheme.text = when (currentTheme) {
            ThemeHelper.THEME_LIGHT -> "Light"
            ThemeHelper.THEME_DARK -> "Dark"
            else -> "System Default"
        }
    }

    private fun showThemeSelection() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetThemeSelectionBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val currentTheme = ThemeHelper.getSelectedTheme(this)
        
        // Highlight current selection
        val primaryColor = getColor(R.color.primary)
        when (currentTheme) {
            ThemeHelper.THEME_LIGHT -> sheetBinding.cardLight.strokeColor = primaryColor
            ThemeHelper.THEME_DARK -> sheetBinding.cardDark.strokeColor = primaryColor
            ThemeHelper.THEME_SYSTEM -> sheetBinding.cardSystem.strokeColor = primaryColor
        }

        val themeMappings = listOf(
            sheetBinding.layoutLightTheme to ThemeHelper.THEME_LIGHT,
            sheetBinding.layoutDarkTheme to ThemeHelper.THEME_DARK,
            sheetBinding.layoutSystemTheme to ThemeHelper.THEME_SYSTEM
        )

        themeMappings.forEach { (layout, theme) ->
            layout.setOnClickListener {
                // Capture location before dismissing
                val location = IntArray(2)
                it.getLocationInWindow(location)
                val x = location[0] + it.width / 2
                val y = location[1] + it.height / 2

                ThemeHelper.saveTheme(this, theme)
                updateThemeText()
                dialog.dismiss()
                
                // Use the captured location for the reveal animation
                animateThemeChange(x, y)
            }
        }

        dialog.show()
    }

    private fun openChannelSettings(channelId: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
            }
            startActivity(intent)
        } else {
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
    }

    private fun updateBatteryStatus() {
        val isIgnoring = NotificationHelper.isIgnoringBatteryOptimizations(this)
        binding.tvBatteryStatus.text = if (isIgnoring) {
            getString(R.string.notification_battery_optimized)
        } else {
            getString(R.string.notification_battery_not_optimized)
        }
        binding.btnOptimizeBattery.visibility = if (isIgnoring) View.GONE else View.VISIBLE
    }

    private fun showTimePicker(currentTime: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
            onTimeSelected(formattedTime)
        }, hour, minute, true).show()
    }

    private fun showPreviewNotification() {
        // Create channels first just in case
        NotificationHelper.createNotificationChannels(this)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Preview Message
        val messageBuilder = NotificationHelper.getBaseNotificationBuilder(this, NotificationHelper.CHANNEL_MESSAGES, NotificationHelper.GROUP_MESSAGES)
            .setContentTitle("Sample Message")
            .setContentText("This is how a message notification looks.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)

        // Preview Booking (Custom Layout)
        val bookingViews = android.widget.RemoteViews(packageName, R.layout.layout_notification_booking)
        bookingViews.setTextViewText(R.id.notification_title, "Booking Confirmed")
        bookingViews.setTextViewText(R.id.notification_body, "Your booking for tomorrow at 10 AM is confirmed.")

        val bookingBuilder = NotificationHelper.getBaseNotificationBuilder(this, NotificationHelper.CHANNEL_BOOKINGS, NotificationHelper.GROUP_BOOKINGS)
            .setCustomContentView(bookingViews)
            .setCustomBigContentView(bookingViews)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

        // Show both for preview
        notificationManager.notify(1001, messageBuilder.build())
        notificationManager.notify(1002, bookingBuilder.build())
        
        showToast("Sample notifications sent!")
    }

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showPreviewNotification()
        } else {
            showToast("Permission denied. Cannot show notifications.")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                showPreviewNotification()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            showPreviewNotification()
        }
    }
}