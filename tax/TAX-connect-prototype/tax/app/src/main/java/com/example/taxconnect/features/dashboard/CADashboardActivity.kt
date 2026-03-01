package com.example.taxconnect.features.dashboard

import android.widget.Toast
import com.example.taxconnect.data.models.UserModel
import androidx.core.graphics.ColorUtils
import android.content.pm.PackageManager
import com.google.firebase.messaging.FirebaseMessaging
import com.example.taxconnect.R
import com.example.taxconnect.core.base.BaseActivity
import android.view.LayoutInflater
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.animation.LayoutTransition
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.taxconnect.features.booking.BookingAdapter
import com.example.taxconnect.databinding.ActivityCaDashboardBinding
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.core.common.Resource
import com.example.taxconnect.core.ui.ThemeHelper
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import com.example.taxconnect.features.auth.LoginActivity
import com.example.taxconnect.features.booking.RequestsActivity
import com.example.taxconnect.features.booking.MyBookingsActivity
import com.example.taxconnect.features.wallet.WalletActivity
import com.example.taxconnect.features.chat.MyChatsActivity
import com.example.taxconnect.features.profile.ProfileActivity
import com.example.taxconnect.features.ca.ExploreCAsActivity
import com.example.taxconnect.features.documents.MyDocumentsActivity
import com.example.taxconnect.features.community.CommunityActivity
import com.example.taxconnect.features.documents.BalanceSheetActivity
import com.example.taxconnect.features.chat.ChatActivity
import com.example.taxconnect.features.milestones.MilestonesActivity
import com.example.taxconnect.features.notification.NotificationSettingsActivity
import com.example.taxconnect.features.notification.NotificationHistoryActivity
import com.example.taxconnect.data.repositories.DataRepository
import android.content.SharedPreferences
import android.os.Build
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.GravityCompat
import java.util.ArrayList
import java.util.Locale
import java.text.NumberFormat

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CADashboardActivity : BaseActivity<ActivityCaDashboardBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityCaDashboardBinding = ActivityCaDashboardBinding::inflate
    private val viewModel: CADashboardViewModel by viewModels()
    private lateinit var bookingAdapter: BookingAdapter
    private var currentUserId: String? = null
    
    private var allAcceptedBookings = listOf<BookingModel>()
    private var showExpired = false
    private var bookingsSortMode = 0 // 0: Date ↑, 1: Date ↓, 2: Urgency

    private val PREFS_DASHBOARD = "ca_dashboard_prefs"
    private val KEY_ONBOARDING_DISMISSED = "onboarding_dismissed"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result
    }

    private val onlineStatusListener = View.OnClickListener {
        // Handled in updateNavHeaderUI
    }

    override fun initViews() {
        currentUserId = FirebaseAuth.getInstance().uid
        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupRecyclerView()
        setupDrawer()
        setupOnboarding()
        setupBookingsControls()
        
        // Initial Load
        currentUserId?.let { uid ->
            viewModel.loadDashboardData(uid)
        }

        askNotificationPermission()
        updateFcmToken()
    }

    override fun onResume() {
        super.onResume()
        currentUserId?.let { uid ->
            viewModel.loadDashboardData(uid)
        }
    }

    override fun setupListeners() {
        setupClickListeners()
    }

    override fun observeViewModel() {
        setupObservers()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.userState.collect { resource ->
                if (resource is Resource.Success) {
                    val user = resource.data
                    if (user != null) {
                        updateNavHeaderUI(user)
                        updateOnlineStatusUI(user.isOnline)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.revenueState.collect { revenue ->
                binding.tvRevenue.text = formatCurrency(revenue)
            }
        }

        lifecycleScope.launch {
            viewModel.revenueBreakdownState.collect { breakdown ->
                binding.tvRevenueToday.text = formatCurrency(breakdown.today)
                binding.tvRevenueWeek.text = formatCurrency(breakdown.thisWeek)
                binding.tvRevenueMonth.text = formatCurrency(breakdown.thisMonth)
                
                // Update graph heights based on weekly revenue (relative)
                updateRevenueGraph(breakdown.thisWeek)
            }
        }

        lifecycleScope.launch {
            viewModel.clientStatsState.collect { stats ->
                binding.tvClientCount.text = stats.active.toString()
                binding.tvReturningClients.text = stats.returning.toString()
            }
        }

        lifecycleScope.launch {
            viewModel.requestStatsState.collect { stats ->
                // Priority logic integrated here
                val priorityCount = viewModel.priorityRequestsState.value.size
                if (priorityCount > 0) {
                    binding.tvPendingRequestsCount.text = getString(R.string.priority_count, priorityCount)
                    binding.tvPendingRequestsCount.setTextColor(ContextCompat.getColor(this@CADashboardActivity, R.color.error_color))
                    binding.badgeRequests.text = priorityCount.toString()
                    binding.badgeRequests.visibility = View.VISIBLE
                    binding.tvPriorityBadge.visibility = View.VISIBLE
                    binding.cardPendingRequests.strokeWidth = resources.getDimensionPixelSize(R.dimen.space_0_5x)
                    binding.cardPendingRequests.strokeColor = ContextCompat.getColor(this@CADashboardActivity, R.color.error_color)
                } else {
                    binding.tvPendingRequestsCount.text = getString(R.string.new_requests_count_dynamic, stats.pending)
                    binding.tvPendingRequestsCount.setTextColor(ContextCompat.getColor(this@CADashboardActivity, R.color.text_muted))
                    binding.badgeRequests.visibility = if (stats.pending > 0) View.VISIBLE else View.GONE
                    if (stats.pending > 0) binding.badgeRequests.text = stats.pending.toString()
                    binding.tvPriorityBadge.visibility = View.GONE
                    binding.cardPendingRequests.strokeWidth = resources.getDimensionPixelSize(R.dimen.space_0_25x)
                    binding.cardPendingRequests.strokeColor = ContextCompat.getColor(this@CADashboardActivity, R.color.primary)
                }
                binding.tvReturningRequestsCount.text = getString(R.string.returning_clients_count, stats.returning)
            }
        }
        
        lifecycleScope.launch {
            viewModel.messageStatsState.collect { stats ->
                val count = stats.unread
                if (count > 0) {
                    binding.badgeChats.text = count.toString()
                    binding.badgeChats.visibility = View.VISIBLE
                } else {
                    binding.badgeChats.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.walletState.collect { balance ->
                binding.tvWalletBalance.text = formatCurrency(balance)
            }
        }

        lifecycleScope.launch {
            viewModel.statusUpdateState.collect { resource ->
                if (resource is Resource.Error) {
                    showToast(resource.message ?: "Unknown error", Toast.LENGTH_SHORT)
                    // Revert UI if needed, but for now we assume optimistic update or just toast error
                    // To revert, we'd need to fetch user again or toggle back
                }
            }
        }

        lifecycleScope.launch {
            viewModel.bookingsState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        // Show loading if needed
                    }
                    is Resource.Success -> {
                        val data = resource.data ?: emptyList()
                        processBookings(data)
                    }
                    is Resource.Error -> {
                        showToast(resource.message ?: "Error loading bookings")
                        binding.tvPendingBookingsCount.text = getString(R.string.new_bookings_count)
                        binding.tvBookingsTitle.text = getString(R.string.upcoming_bookings)
                        binding.tvEmptyState.text = getString(R.string.error_loading_bookings)
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvBookings.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun processBookings(data: List<BookingModel>) {
        val acceptedList = ArrayList<BookingModel>()
        var pendingCount = 0

        for (booking in data) {
            if ("ACCEPTED".equals(booking.status, ignoreCase = true)) {
                acceptedList.add(booking)
            } else if ("PENDING".equals(booking.status, ignoreCase = true)) {
                pendingCount++
            }
        }

        binding.tvPendingBookingsCount.text = getString(R.string.new_bookings_count_dynamic, pendingCount)
        if (pendingCount > 0) {
            binding.tvPendingBookingsCount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.badgeBookings.text = pendingCount.toString()
            binding.badgeBookings.visibility = View.VISIBLE
        } else {
            binding.tvPendingBookingsCount.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.badgeBookings.visibility = View.GONE
        }

        allAcceptedBookings = acceptedList
        applyBookingsFiltersAndSort()
    }

    private fun applyBookingsFiltersAndSort() {
        val filtered = ArrayList<BookingModel>()
        val now = System.currentTimeMillis()
        
        for (b in allAcceptedBookings) {
            val expired = b.appointmentTimestamp < now
            if (showExpired) {
                if (expired) filtered.add(b)
            } else {
                if (!expired) filtered.add(b)
            }
        }

        if (filtered.isEmpty()) {
            binding.tvBookingsTitle.text = if (showExpired) getString(R.string.expired_bookings) else getString(R.string.upcoming_bookings_title)
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvBookings.visibility = View.GONE
            return
        }

        filtered.sortWith { a, b ->
            when (bookingsSortMode) {
                0 -> a.appointmentTimestamp.compareTo(b.appointmentTimestamp) // Date ↑
                1 -> b.appointmentTimestamp.compareTo(a.appointmentTimestamp) // Date ↓
                else -> {
                    val da = Math.abs(a.appointmentTimestamp - now)
                    val db = Math.abs(b.appointmentTimestamp - now)
                    da.compareTo(db) // Urgency
                }
            }
        }

        binding.tvBookingsTitle.text = if (showExpired) 
            getString(R.string.expired_bookings_count, filtered.size) 
        else 
            getString(R.string.upcoming_bookings_count, filtered.size)
        binding.tvEmptyState.visibility = View.GONE
        binding.rvBookings.visibility = View.VISIBLE
        bookingAdapter.setBookings(filtered)
    }

    private fun setupRecyclerView() {
        bookingAdapter = BookingAdapter(object : BookingAdapter.OnBookingActionListener {
            override fun onAccept(booking: BookingModel) {
                viewModel.updateBookingStatus(booking, "ACCEPTED")
            }

            override fun onReject(booking: BookingModel) {
                viewModel.updateBookingStatus(booking, "REJECTED")
            }

            override fun onBookingClick(booking: BookingModel) {
                if ("ACCEPTED" == booking.status) {
                    val intent = Intent(this@CADashboardActivity, ChatActivity::class.java)
                    intent.putExtra("bookingId", booking.id)
                    intent.putExtra("userId", booking.userId)
                    intent.putExtra("userName", booking.userName)
                    startActivity(intent)
                }
            }

            override fun onMilestonesClick(booking: BookingModel) {
                val intent = Intent(this@CADashboardActivity, MilestonesActivity::class.java)
                intent.putExtra("bookingId", booking.id)
                startActivity(intent)
            }

            override fun onAddToCalendar(booking: BookingModel) {
                // Add booking to calendar
                val intent = Intent(Intent.ACTION_INSERT)
                intent.data = android.provider.CalendarContract.Events.CONTENT_URI
                intent.putExtra(android.provider.CalendarContract.Events.TITLE, "Appointment with ${booking.userName ?: "Client"}")
                intent.putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Booking ID: ${booking.id}")
                intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, booking.appointmentTimestamp)
                intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, booking.appointmentTimestamp + (60 * 60 * 1000)) // 1 hour duration
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    showToast("No calendar app found")
                }
            }
        }, true) // isCaView = true

        binding.rvBookings.layoutManager = LinearLayoutManager(this)
        binding.rvBookings.adapter = bookingAdapter
    }

    private fun setupClickListeners() {
        binding.ivMyChats.setOnClickListener { startActivity(Intent(this, MyChatsActivity::class.java)) }
        binding.ivNotifications.setOnClickListener { startActivity(Intent(this, NotificationHistoryActivity::class.java)) }
        binding.ivProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        binding.cardRevenue.setOnClickListener { startActivity(Intent(this, BalanceSheetActivity::class.java)) }
        binding.cardActiveClients.setOnClickListener { startActivity(Intent(this, MyChatsActivity::class.java)) }
        binding.cardPendingRequests.setOnClickListener { startActivity(Intent(this, RequestsActivity::class.java)) }
        binding.btnViewAllRequests.setOnClickListener { startActivity(Intent(this, RequestsActivity::class.java)) }
        binding.cardPendingBookings.setOnClickListener { startActivity(Intent(this, MyBookingsActivity::class.java)) }
        binding.btnViewAllBookings.setOnClickListener { startActivity(Intent(this, MyBookingsActivity::class.java)) }
        binding.cardExplore.setOnClickListener { startActivity(Intent(this, ExploreCAsActivity::class.java)) }
        
        // Quick Actions (Cards)
        binding.cardQuickRequests.setOnClickListener { startActivity(Intent(this, RequestsActivity::class.java)) }
        binding.cardQuickBookings.setOnClickListener { startActivity(Intent(this, MyBookingsActivity::class.java)) }
        binding.cardQuickWallet.setOnClickListener { startActivity(Intent(this, WalletActivity::class.java)) }
        binding.cardQuickChats.setOnClickListener { startActivity(Intent(this, MyChatsActivity::class.java)) }
        
        binding.ivHelp.setOnClickListener { showDashboardHelpDialog() }
        
        binding.switchStatusToggle.setOnCheckedChangeListener { _, isChecked ->
            val userResource = viewModel.userState.value
            if (userResource is Resource.Success) {
                val currentUser = userResource.data
                if (currentUser != null && currentUser.isOnline != isChecked) {
                    handleStatusChange(isChecked)
                }
            }
        }
        
        // Layout animation for dashboard elements
        val layoutConfig = LayoutTransition()
        layoutConfig.setDuration(300)
        layoutConfig.enableTransitionType(LayoutTransition.CHANGING)
        binding.layoutStats.layoutTransition = layoutConfig
    }
    
    private fun setupOnboarding() {
        val prefs = getSharedPreferences(PREFS_DASHBOARD, MODE_PRIVATE)
        val dismissed = prefs.getBoolean(KEY_ONBOARDING_DISMISSED, false)
        binding.cardOnboarding.visibility = if (dismissed) View.GONE else View.VISIBLE
        
        binding.btnOnboardingDismiss.setOnClickListener {
            prefs.edit().putBoolean(KEY_ONBOARDING_DISMISSED, true).apply()
            binding.cardOnboarding.visibility = View.GONE
        }
        binding.btnOnboardingHelp.setOnClickListener { showDashboardHelpDialog() }
    }
    
    private fun showDashboardHelpDialog() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(getString(R.string.dashboard_help_message), android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(getString(R.string.dashboard_help_message))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dashboard_help_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.got_it), null)
            .show()
    }
    
    private fun setupBookingsControls() {
        val sortOptions = arrayOf(
            getString(R.string.sort_date_asc),
            getString(R.string.sort_date_desc),
            getString(R.string.sort_urgency)
        )
        val sortAdapter = ArrayAdapter(this, R.layout.item_spinner, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBookingsSort.adapter = sortAdapter
        binding.spinnerBookingsSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bookingsSortMode = position
                applyBookingsFiltersAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        binding.chipUpcoming.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showExpired = false
                applyBookingsFiltersAndSort()
            }
        }
        binding.chipExpired.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showExpired = true
                applyBookingsFiltersAndSort()
            }
        }
    }

    private fun setupDrawer() {
        binding.ivMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { /* Already here */ }
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_chats -> startActivity(Intent(this, MyChatsActivity::class.java))
                R.id.nav_wallet -> startActivity(Intent(this, WalletActivity::class.java))
                R.id.nav_requests -> startActivity(Intent(this, RequestsActivity::class.java))
                R.id.nav_bookings -> startActivity(Intent(this, MyBookingsActivity::class.java))
                R.id.nav_explore -> startActivity(Intent(this, ExploreCAsActivity::class.java))
                R.id.nav_docs -> startActivity(Intent(this, MyDocumentsActivity::class.java))
                R.id.nav_community -> startActivity(Intent(this, CommunityActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, NotificationSettingsActivity::class.java))
                R.id.nav_help -> showDashboardHelpDialog()
                R.id.nav_logout -> {
                    val uid = FirebaseAuth.getInstance().uid
                    if (uid != null) {
                        DataRepository.getInstance().clearUserCache(uid)
                    }
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun updateNavHeaderUI(user: UserModel) {
        // Update Nav Header if exists
        val headerView = binding.navView.getHeaderView(0)
        if (headerView != null) {
            val navName = headerView.findViewById<TextView>(R.id.nav_header_name)
            val navEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
            val navImage = headerView.findViewById<ImageView>(R.id.nav_header_image)

            navName?.text = user.name
            navEmail?.text = user.email
            
            navImage?.let {
                Glide.with(this)
                    .load(user.profileImageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .circleCrop()
                    .into(it)
            }
        }
        
        binding.tvWelcomeName.text = "CA ${user.name}"

        // Update rating display in dashboard cards
        if (user.role == "CA") {
            binding.tvRating.text = String.format(Locale.getDefault(), "%.1f", user.rating)
            binding.tvRatingCount.text = getString(R.string.rating_count_format, user.ratingCount)
        }

        Glide.with(this)
            .load(user.profileImageUrl)
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .circleCrop()
            .into(binding.ivProfile)

        // Set initial status state
        updateOnlineStatusUI(user.isOnline)
    }

    private fun handleStatusChange(isOnline: Boolean) {
        updateOnlineStatusUI(isOnline)
        currentUserId?.let { uid ->
            viewModel.updateUserStatus(uid, isOnline)
        }
    }

    private fun updateOnlineStatusUI(isOnline: Boolean) {
        // Temporarily detach listener to avoid re-triggering handleStatusChange
        binding.switchStatusToggle.setOnCheckedChangeListener(null)
        binding.switchStatusToggle.isChecked = isOnline
        
        if (isOnline) {
            binding.switchStatusToggle.text = "Online"
            binding.switchStatusToggle.setTextColor(ContextCompat.getColor(this, R.color.emerald_600))
        } else {
            binding.switchStatusToggle.text = "Offline"
            binding.switchStatusToggle.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
        
        // Reattach listener
        binding.switchStatusToggle.setOnCheckedChangeListener { _, isChecked ->
            val userResource = viewModel.userState.value
            if (userResource is Resource.Success) {
                val currentUser = userResource.data
                if (currentUser != null && currentUser.isOnline != isChecked) {
                    handleStatusChange(isChecked)
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.result?.let { token ->
                    DataRepository.getInstance().updateFcmToken(token)
                }
            }
        }
    }

    private fun updateRevenueGraph(weeklyTotal: Double) {
        val maxBarHeight = resources.getDimensionPixelSize(R.dimen.space_30x) // ~120dp
        
        // Simulating some activity based on the total
        // If weeklyTotal is 0, we show very small bars to indicate empty state but keep UI structure
        val baseFactor = if (weeklyTotal > 0) 1.0 else 0.1
        
        // Weekly distribution simulation (relative weights)
        // Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
        val weights = listOf(0.4f, 0.75f, 0.55f, 1.0f, 0.8f, 0.35f, 0.25f)
        val bars = listOf(
            binding.barMon, binding.barTue, binding.barWed, 
            binding.barThu, binding.barFri, binding.barSat, binding.barSun
        )

        val primaryColor = ContextCompat.getColor(this, R.color.secondary)
        val containerColor = ContextCompat.getColor(this, R.color.primary_container)

        bars.forEachIndexed { index, view ->
            val params = view.layoutParams
            val targetHeight = (maxBarHeight * weights[index] * baseFactor).toInt().coerceAtLeast(
                resources.getDimensionPixelSize(R.dimen.space_1x)
            )
            
            // Simple animation-like effect (immediate but structured)
            params.height = targetHeight
            view.layoutParams = params
            
            // Highlight Thursday as peak day in simulation
            if (index == 3 && weeklyTotal > 0) {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            } else {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(containerColor)
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        var safeAmount = amount
        if (safeAmount.isNaN() || safeAmount.isInfinite()) {
            safeAmount = 0.0
        }
        safeAmount = Math.max(0.0, safeAmount)
        val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        return formatter.format(safeAmount)
    }
}