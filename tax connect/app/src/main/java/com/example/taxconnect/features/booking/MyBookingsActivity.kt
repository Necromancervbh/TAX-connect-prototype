package com.example.taxconnect.features.booking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.BookingRepository
import com.example.taxconnect.features.booking.BookingAdapter
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.databinding.ActivityMyBookingsBinding
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.features.chat.ChatActivity
import com.example.taxconnect.features.milestones.MilestonesActivity
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.taxconnect.core.utils.CalendarHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.Manifest

class MyBookingsActivity : BaseActivity<ActivityMyBookingsBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityMyBookingsBinding = ActivityMyBookingsBinding::inflate

    private var adapter: BookingAdapter? = null
    private lateinit var repository: DataRepository
    private val bookingRepo = BookingRepository()
    private var allBookings: MutableList<BookingModel> = mutableListOf()
    private var bookingsListener: ListenerRegistration? = null
    private var currentUserId: String? = null
    private var isCA: Boolean = false
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
        currentUserId = FirebaseAuth.getInstance().uid

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_material3)

        setupTabs()
        setupRecyclerView()
        fetchUserRoleThenListen()
    }

    override fun onResume() {
        super.onResume()
        // Live listener handles updates automatically — no extra load needed
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.layoutEmptyState.btnEmptyAction.setOnClickListener { fetchUserRoleThenListen() }
    }

    override fun observeViewModel() {
        // No ViewModel — data observed via Firestore listener
    }

    override fun onStop() {
        super.onStop()
        bookingsListener?.remove()
        bookingsListener = null
    }

    /**
     * Fetches the user's role, updates the adapter, then attaches a live Firestore
     * snapshot listener so the list updates automatically without manual refresh.
     */
    private fun fetchUserRoleThenListen() {
        val uid = currentUserId ?: run { finish(); return }

        if (allBookings.isEmpty()) {
            binding.shimmerViewContainer.root.visibility = View.VISIBLE
            binding.shimmerViewContainer.root.startShimmer()
        }

        repository.fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                if (data != null) {
                    isCA = data.role == "CA"
                    updateAdapter(isCA)
                    attachLiveListener(uid, isCA)
                } else {
                    showListError("Could not determine user role.")
                }
            }
            override fun onError(error: String?) {
                showListError("Failed to load: $error")
            }
        })
    }

    private fun attachLiveListener(uid: String, isCa: Boolean) {
        bookingsListener?.remove()
        val db = FirebaseFirestore.getInstance()
        val field = if (isCa) "caId" else "userId"
        bookingsListener = db.collection("bookings")
            .whereEqualTo(field, uid)
            .addSnapshotListener { snapshot, error ->
                binding.shimmerViewContainer.root.stopShimmer()
                binding.shimmerViewContainer.root.visibility = View.GONE
                if (error != null || snapshot == null) {
                    showListError("Failed to load bookings.")
                    return@addSnapshotListener
                }
                val rawList = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
                    .sortedByDescending { it.appointmentTimestamp }
                allBookings = rawList.toMutableList()
                if (isCa) {
                    // Enrich with client names for CA view
                    enrichBookingsWithUserNames(allBookings)
                } else {
                    // Enrich with CA names for client view
                    enrichBookingsWithCaNames(allBookings)
                }
                filterBookings()
            }
    }

    private fun enrichBookingsWithUserNames(bookings: List<BookingModel>) {
        bookings.forEach { booking ->
            val userId = booking.userId ?: return@forEach
            if (!booking.userName.isNullOrBlank()) return@forEach
            DataRepository.getInstance().fetchUser(userId, object : DataRepository.DataCallback<UserModel> {
                override fun onSuccess(data: UserModel?) {
                    if (data != null) {
                        booking.userName = data.name
                        runOnUiThread { adapter?.notifyDataSetChanged() }
                    }
                }
                override fun onError(error: String?) {}
            })
        }
    }

    private fun enrichBookingsWithCaNames(bookings: List<BookingModel>) {
        bookings.forEach { booking ->
            val caId = booking.caId ?: return@forEach
            if (!booking.caName.isNullOrBlank()) return@forEach // already have name
            DataRepository.getInstance().fetchUser(caId, object : DataRepository.DataCallback<UserModel> {
                override fun onSuccess(data: UserModel?) {
                    if (data != null) {
                        booking.caName = data.name
                        runOnUiThread { adapter?.notifyDataSetChanged() }
                    }
                }
                override fun onError(error: String?) {}
            })
        }
    }

    private fun showListError(message: String) {
        binding.shimmerViewContainer.root.stopShimmer()
        binding.shimmerViewContainer.root.visibility = View.GONE
        binding.rvBookings.visibility = View.GONE
        binding.layoutEmptyState.root.visibility = View.GONE
        binding.layoutErrorState.root.visibility = View.VISIBLE
        binding.layoutErrorState.tvErrorTitle.text = "Error"
        binding.layoutErrorState.tvErrorDescription.text = message
        binding.layoutErrorState.btnRetry.setOnClickListener {
            binding.layoutErrorState.root.visibility = View.GONE
            fetchUserRoleThenListen()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Pending"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_upcoming)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_declined)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Completed"))

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
        var emptyTitle = "No Pending Bookings"
        var emptyDesc = "You have no pending booking requests."
        var emptyIcon = R.drawable.ic_calendar

        when (position) {
            0 -> { // Pending
                filteredList.addAll(allBookings.filter { it.status == "PENDING" })
            }
            1 -> { // Upcoming (ACCEPTED)
                filteredList.addAll(allBookings.filter { it.status == "ACCEPTED" || it.status == "CONFIRMED" })
                emptyTitle = "No Upcoming Bookings"
                emptyDesc = "You don't have any upcoming bookings scheduled."
                emptyIcon = R.drawable.ic_event
            }
            2 -> { // Declined
                filteredList.addAll(allBookings.filter { it.status == "REJECTED" || it.status == "CANCELLED" || it.status == "EXPIRED" })
                emptyTitle = "No Declined Bookings"
                emptyDesc = "You don't have any declined or cancelled bookings."
                emptyIcon = R.drawable.ic_history
            }
            3 -> { // Completed
                filteredList.addAll(allBookings.filter { it.status == "COMPLETED" })
                emptyTitle = "No Completed Bookings"
                emptyDesc = "Completed bookings will appear here."
                emptyIcon = R.drawable.ic_check_circle
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
            binding.layoutEmptyState.btnEmptyAction.setOnClickListener { fetchUserRoleThenListen() }
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


    private fun updateAdapter(isCa: Boolean) {
        val listener = object : BookingAdapter.OnBookingActionListener {
            override fun onAccept(booking: BookingModel) {
                if (isCa) {
                    lifecycleScope.launch {
                        try {
                            // Fetch current CA's name to personalise the client FCM notification
                            val caName = try {
                                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("users").document(currentUserId ?: "").get()
                                    .await()
                                doc.getString("name")
                            } catch (_: Exception) { null }

                            val convId = bookingRepo.acceptBookingWithChat(booking, caName)
                            allBookings.find { it.id == booking.id }?.status = "ACCEPTED"
                            filterBookings()
                            showToast("Booking accepted! Chat created.")
                            if (convId != null) {
                                val intent = Intent(this@MyBookingsActivity, ChatActivity::class.java).apply {
                                    putExtra("chatId", convId)
                                    putExtra("otherUserId", booking.userId)
                                    putExtra("otherUserName", booking.userName)
                                    putExtra("bookingId", booking.id)
                                }
                                startActivity(intent)
                            }
                        } catch (e: Exception) {
                            showToast("Failed to accept booking: ${e.message}")
                        }
                    }
                }
            }

            override fun onReject(booking: BookingModel) {
                if (isCa) {
                    updateBookingStatus(booking, "REJECTED")
                }
            }

            override fun onBookingClick(booking: BookingModel) {
                // PENDING bookings have no chatId yet — chat is created when CA accepts.
                if (booking.status == "PENDING") {
                    if (isCa) {
                        showToast("Accept the booking first to start a chat.")
                    } else {
                        showToast("Waiting for the CA to accept your booking.")
                    }
                    return
                }

                val chatId = booking.chatId
                val receiverId = if (isCa) booking.userId else booking.caId
                val receiverName = if (isCa) booking.userName else booking.caName

                if (receiverId == null) {
                    showToast(getString(R.string.user_info_missing))
                    return
                }

                // For ACCEPTED bookings: validate chatId is present.
                // Old/partially-written bookings may lack it — fetch live from Firestore.
                if (booking.status == "ACCEPTED" && chatId.isNullOrBlank()) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("bookings").document(booking.id ?: return)
                        .get()
                        .addOnSuccessListener { doc ->
                            val freshChatId = doc.getString("chatId")
                            if (freshChatId.isNullOrBlank()) {
                                showToast("Chat not ready yet. Please try again shortly.")
                                return@addOnSuccessListener
                            }
                            val intent = Intent(this@MyBookingsActivity, ChatActivity::class.java).apply {
                                putExtra("chatId", freshChatId)
                                putExtra("otherUserId", receiverId)
                                putExtra("otherUserName", receiverName)
                                putExtra("bookingId", booking.id)
                            }
                            startActivity(intent)
                        }
                        .addOnFailureListener {
                            showToast("Failed to load chat. Please try again.")
                        }
                    return
                }

                val intent = Intent(this@MyBookingsActivity, ChatActivity::class.java).apply {
                    putExtra("otherUserId", receiverId)
                    putExtra("otherUserName", receiverName)
                    putExtra("bookingId", booking.id)
                    if (!chatId.isNullOrBlank()) putExtra("chatId", chatId)
                }
                startActivity(intent)
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

            override fun onMarkComplete(booking: BookingModel) {
                if (isCa) {
                    showMarkCompleteDialog(booking)
                }
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

    private fun showMarkCompleteDialog(booking: BookingModel) {
        val service = booking.serviceName ?: "service"
        val client = booking.userName ?: "the client"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mark as Complete")
            .setMessage("Confirm booking for '$service' with $client is completed? The client will be notified.")
            .setPositiveButton("Yes, Complete") { _, _ ->
                updateBookingStatus(booking, "COMPLETED")
                // Also transition the linked conversation to STATE_COMPLETED so the
                // client's chat stepper finishes and the rating prompt fires.
                val chatId = booking.chatId
                if (!chatId.isNullOrBlank()) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("conversations")
                        .document(chatId)
                        .update("workflowState",
                            com.example.taxconnect.data.models.ConversationModel.STATE_COMPLETED)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}
