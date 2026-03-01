package com.example.taxconnect.features.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import com.example.taxconnect.R
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.DataRepository.DataCallback
import com.example.taxconnect.databinding.ActivityMainBinding
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.features.dashboard.CADashboardActivity
import com.example.taxconnect.features.home.HomeActivity
import com.example.taxconnect.features.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.example.taxconnect.core.utils.NotificationHelper

class MainActivity : BaseActivity<ActivityMainBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityMainBinding = ActivityMainBinding::inflate
    private lateinit var repository: DataRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showToast(getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT)
        } else {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                showPermissionRationaleDialog()
            } else {
                showToast(getString(R.string.notification_permission_denied))
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Notifications Permission")
            .setMessage("TaxConnect needs notification permissions to alert you about new messages, calls, and bookings. Please enable them in settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun initViews() {
        // Create notification channels
        NotificationHelper.createNotificationChannels(this)

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Initialize Repository
        try {
            repository = DataRepository.getInstance()
        } catch (e: Exception) {
             android.util.Log.e("MainActivity", "Repo init failed", e)
        }

        // Check if user is already logged in
        if (FirebaseAuth.getInstance().currentUser != null) {
            checkUserRoleAndRedirect()
        }
    }

    override fun observeViewModel() {
        // No ViewModel to observe
    }

    override fun setupListeners() {
        binding.btnCustomer.setOnClickListener { startRegisterActivity("CUSTOMER") }
        binding.btnCA.setOnClickListener { startRegisterActivity("CA") }
        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkUserRoleAndRedirect() {
        try {
            val current = FirebaseAuth.getInstance().currentUser
            if (current == null) {
                return
            }
            val uid = current.uid
            DataRepository.getInstance().fetchUser(uid, object : DataCallback<UserModel> {
                override fun onSuccess(data: UserModel?) {
                    if (data == null) {
                        try {
                            FirebaseAuth.getInstance().signOut()
                        } catch (e: Exception) {}
                        return
                    }
                    
                    try {
                        val intent = if ("CA".equals(data.role, ignoreCase = true)) {
                            Intent(this@MainActivity, CADashboardActivity::class.java)
                        } else {
                            Intent(this@MainActivity, HomeActivity::class.java)
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        showToast("Navigation error: ${e.message}", Toast.LENGTH_SHORT)
                    }
                }

                override fun onError(error: String?) {
                    showToast("Error fetching user: $error", Toast.LENGTH_SHORT)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Redirect failed", e)
        }
    }

    private fun startRegisterActivity(role: String) {
        val intent = Intent(this, com.example.taxconnect.features.auth.RegisterActivity::class.java)
        intent.putExtra("ROLE", role)
        startActivity(intent)
    }
}
