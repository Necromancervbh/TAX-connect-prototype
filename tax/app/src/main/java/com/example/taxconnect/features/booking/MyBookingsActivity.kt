package com.example.taxconnect.features.booking

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.features.booking.BookingAdapter
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.databinding.ActivityMyBookingsBinding
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.features.chat.ChatActivity
import com.example.taxconnect.features.milestones.MilestonesActivity
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.example.taxconnect.core.utils.CalendarHelper
import com.google.android.material.snackbar.Snackbar
import android.Manifest

class MyBookingsActivity : BaseActivity<ActivityMyBookingsBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityMyBookingsBinding = ActivityMyBookingsBinding::inflate

    private var adapter: BookingAdapter? = null
    private lateinit var repository: DataRepository
    private var allBookings: MutableList<BookingModel> = mutableListOf()
    private val retryHandler = Handler(Looper.getMainLooper())
    private var pendingBookingForCalendar: BookingModel? = null

    // Calendar permission launcher
    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            pendingBookingForCalendar?.let { addToCalendar(it) }
        } else {
            Snackbar.make(
                binding.root,
                getString(R.string.calendar_permission_required),
                Snackbar.LENGTH_LONG
            ).show()
        }
        pendingBookingForCalendar = null
    }

    override fun initViews() {
        repository = DataRepository.getInstance()

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_material3)

        setupTabs()
        setupRecyclerView()
        loadBookings()
    }

    override fun onResume() {
        super.onResume()
        loadBookings()
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        // Default retry action
        binding.layoutEmptyState.btnEmptyAction.setOnClickListener { loadBookings() }
    }

    override fun observeViewModel() {
        // No ViewModel used in this activity yet
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_requests)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_upcoming)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_declined)))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filterBookings()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun filterBookings() {
        val position = binding.tabLayout.selectedTabPosition
        val filteredList = mutableListOf<BookingModel>()
        var targetStatus = "PENDING"
        
        var emptyTitle = "No Requests"
        var emptyDesc = "You don't have any pending booking requests."
        var emptyIcon = R.drawable.ic_calendar

        when (position) {
            1 -> {
                targetStatus = "ACCEPTED"
                emptyTitle = "No Upcoming Bookings"
                emptyDesc = "You don't have any upcoming bookings scheduled."
                emptyIcon = R.drawable.ic_event
            }
            2 -> {
                targetStatus = "REJECTED"
                emptyTitle = "No Declined Bookings"
                emptyDesc = "You don't have any declined or cancelled bookings."
                emptyIcon = R.drawable.ic_history
            }
        }

        for (booking in allBookings) {
            val status = booking.status
            if (targetStatus == status) {
                filteredList.add(booking)
            } else if (position == 1 && (status == "CONFIRMED" || status == "COMPLETED")) {
                // Group confirmed/completed with accepted
                filteredList.add(booking)
            } else if (position == 2 && status == "CANCELLED") {
                // Group cancelled with refused
                filteredList.add(booking)
            }
        }

        adapter?.setBookings(filteredList)

        if (filteredList.isEmpty()) {
            binding.rvBookings.visibility = View.GONE
            binding.layoutEmptyState.root.visibility = View.VISIBLE
            
            binding.layoutEmptyState.tvEmptyTitle.text = emptyTitle
            binding.layoutEmptyState.tvEmptyDescription.text = emptyDesc
            binding.layoutEmptyState.ivEmptyIcon.setImageResource(emptyIcon)
            
            binding.layoutEmptyState.btnEmptyAction.text = "Refresh"
            binding.layoutEmptyState.btnEmptyAction.setOnClickListener { loadBookings() }
        } else {
            binding.rvBookings.visibility = View.VISIBLE
            binding.layoutEmptyState.root.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        // Initial setup, will be updated after user role fetch
        adapter = BookingAdapter(null, false)
        binding.rvBookings.layoutManager = LinearLayoutManager(this)
        binding.rvBookings.adapter = adapter
    }

    private fun loadBookings() {
        loadBookingsWithRetry(0)
    }

    private fun loadBookingsWithRetry(attempt: Int) {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            finish()
            return
        }

        if (allBookings.isEmpty()) {
            binding.shimmerViewContainer.root.visibility = View.VISIBLE
            binding.shimmerViewContainer.root.startShimmer()
            binding.layoutEmptyState.root.visibility = View.GONE
            binding.rvBookings.visibility = View.GONE
        }

        repository.fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                if (data != null) {
                    val isCa = "CA" == data.role
                    updateAdapter(isCa)

                    if (isCa) {
                        repository.getBookingsForCA(uid, bookingCallback(attempt))
                    } else {
                        repository.getBookingsForUser(uid, bookingCallback(attempt))
                    }
                } else {
                    showLoadError("Failed to load bookings. Please try again.", attempt)
                }
            }

            override fun onError(error: String?) {
                showLoadError("Failed to load bookings. Please try again.", attempt)
            }
        })
    }

    private fun updateAdapter(isCa: Boolean) {
        val listener = object : BookingAdapter.OnBookingActionListener {
            override fun onAccept(booking: BookingModel) {
                if (isCa) {
                    updateBookingStatus(booking, "ACCEPTED")
                }
            }

            override fun onReject(booking: BookingModel) {
                if (isCa) {
                    updateBookingStatus(booking, "REJECTED")
                }
            }

            override fun onBookingClick(booking: BookingModel) {
                val receiverId = if (isCa) booking.userId else booking.caId
                val receiverName = if (isCa) booking.userName else booking.caName

                if (receiverId != null) {
                    val intent = Intent(this@MyBookingsActivity, ChatActivity::class.java).apply {
                        putExtra("otherUserId", receiverId)
                        putExtra("otherUserName", receiverName)
                        putExtra("bookingId", booking.id)
                    }
                    startActivity(intent)
                } else {
                    showToast(getString(R.string.user_info_missing))
                }
            }

            override fun onMilestonesClick(booking: BookingModel) {
                val intent = Intent(this@MyBookingsActivity, MilestonesActivity::class.java).apply {
                    putExtra("bookingId", booking.id)
                }
                startActivity(intent)
            }

            override fun onAddToCalendar(booking: BookingModel) {
                handleAddToCalendar(booking)
            }
        }

        adapter = BookingAdapter(listener, isCa)
        binding.rvBookings.adapter = adapter
    }

    private fun updateBookingStatus(booking: BookingModel, status: String) {
        val bookingId = booking.id ?: return
        repository.updateBookingStatus(bookingId, status, object : DataRepository.DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                if ("ACCEPTED" == status) {
                    val caId = booking.caId
                    val userId = booking.userId
                    if (caId != null && userId != null) {
                        repository.incrementClientCount(caId, userId)
                    }
                }
                showToast(getString(R.string.booking_status_updated, status))
                
                // Update local list
                allBookings.find { it.id == booking.id }?.status = status
                filterBookings()
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_update_booking, error))
            }
        })
    }

    private fun bookingCallback(attempt: Int): DataRepository.DataCallback<List<BookingModel>> {
        return object : DataRepository.DataCallback<List<BookingModel>> {
            override fun onSuccess(bookings: List<BookingModel>?) {
                binding.shimmerViewContainer.root.stopShimmer()
                binding.shimmerViewContainer.root.visibility = View.GONE
                allBookings = bookings?.toMutableList() ?: mutableListOf()
                filterBookings()
            }

            override fun onError(error: String?) {
                showLoadError("Failed to load bookings. Please try again.", attempt)
            }
        }
    }

    private fun showLoadError(message: String, attempt: Int) {
        if (attempt < 2) {
            retryHandler.postDelayed({ loadBookingsWithRetry(attempt + 1) }, (attempt + 1) * 800L)
            return
        }
        binding.shimmerViewContainer.root.stopShimmer()
        binding.shimmerViewContainer.root.visibility = View.GONE
        binding.rvBookings.visibility = View.GONE
        binding.layoutEmptyState.root.visibility = View.GONE
        
        // Show error state
        val errorBinding = binding.layoutErrorState
        errorBinding.root.visibility = View.VISIBLE
        errorBinding.tvErrorTitle.text = "Error Loading Bookings"
        errorBinding.tvErrorDescription.text = message
        errorBinding.btnRetry.setOnClickListener { 
            errorBinding.root.visibility = View.GONE
            loadBookingsWithRetry(0) 
        }
    }


    private fun handleAddToCalendar(booking: BookingModel) {
        if (CalendarHelper.hasCalendarPermission(this)) {
            addToCalendar(booking)
        } else {
            pendingBookingForCalendar = booking
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    private fun addToCalendar(booking: BookingModel) {
        val eventId = CalendarHelper.addBookingToCalendar(this, booking)
        if (eventId != null) {
            Snackbar.make(
                binding.root,
                getString(R.string.event_added_to_calendar),
                Snackbar.LENGTH_LONG
            ).setAction("View") {
                // Optional: Open calendar app
            }.show()
        } else {
            Snackbar.make(
                binding.root,
                getString(R.string.failed_to_add_event),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}
