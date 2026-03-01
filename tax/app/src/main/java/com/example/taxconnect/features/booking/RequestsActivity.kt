package com.example.taxconnect.features.booking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.features.booking.RequestsAdapter
import com.example.taxconnect.databinding.ActivityRequestsBinding
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.RequestItem
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.DataRepository
import com.google.firebase.auth.FirebaseAuth
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.features.auth.LoginActivity

class RequestsActivity : BaseActivity<ActivityRequestsBinding>(), RequestsAdapter.OnRequestActionListener {

    override val bindingInflater: (LayoutInflater) -> ActivityRequestsBinding = ActivityRequestsBinding::inflate
    private lateinit var repository: DataRepository
    private lateinit var adapter: RequestsAdapter
    private var currentUserId: String? = null
    private val allRequests = mutableListOf<RequestItem>()
    private var conversationRequests = listOf<ConversationModel>()
    private val bookingRequests = mutableListOf<BookingModel>()

    override fun initViews() {
        repository = DataRepository.getInstance()
        currentUserId = FirebaseAuth.getInstance().uid
        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.toolbar.title = getString(R.string.tab_requests)

        setupRecyclerView()
        fetchRequests()
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeViewModel() {}

    private fun setupRecyclerView() {
        adapter = RequestsAdapter(this)
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun fetchRequests() {
        val uid = currentUserId ?: return

        // Fetch Messaging Requests
        repository.getRequests(uid, object : DataRepository.DataCallback<List<ConversationModel>> {
            override fun onSuccess(data: List<ConversationModel>?) {
                conversationRequests = data ?: emptyList()
                updateMergedList()
            }

            override fun onError(error: String?) {
                showToast("Error fetching requests: $error")
            }
        })

        // Fetch Booking Requests
        repository.getBookingsForCA(uid, object : DataRepository.DataCallback<List<BookingModel>> {
            override fun onSuccess(data: List<BookingModel>?) {
                bookingRequests.clear()
                data?.forEach { model ->
                    if ("PENDING" == model.status) {
                        bookingRequests.add(model)
                    }
                }
                updateMergedList()
            }

            override fun onError(error: String?) {
                // Ignore error or log it, as user might not be a CA or have no bookings
            }
        })
    }

    private fun updateMergedList() {
        allRequests.clear()
        conversationRequests.forEach { model ->
            allRequests.add(RequestItem(RequestItem.TYPE_CONVERSATION, model))
        }
        bookingRequests.forEach { model ->
            allRequests.add(RequestItem(RequestItem.TYPE_BOOKING, model))
        }

        if (allRequests.isEmpty()) {
            binding.rvRequests.visibility = android.view.View.GONE
            binding.layoutEmptyState.root.visibility = android.view.View.VISIBLE
            binding.layoutEmptyState.tvEmptyTitle.text = getString(R.string.no_requests_title)
            binding.layoutEmptyState.tvEmptyDescription.text = getString(R.string.no_requests_desc)
            binding.layoutEmptyState.btnEmptyAction.visibility = android.view.View.GONE
        } else {
            binding.rvRequests.visibility = android.view.View.VISIBLE
            binding.layoutEmptyState.root.visibility = android.view.View.GONE
        }

        updateAdapter()
        enrichRequestsWithUserData()
    }

    private fun enrichRequestsWithUserData() {
        allRequests.forEach { item ->
            // Skip if already has user model
            if (item.userModel != null) return@forEach

            val targetUid = when (item.type) {
                RequestItem.TYPE_CONVERSATION -> {
                    val conv = item.data as ConversationModel
                    // Find participant that is NOT current user
                    conv.participantIds?.firstOrNull { it != currentUserId }
                }
                RequestItem.TYPE_BOOKING -> {
                    val booking = item.data as BookingModel
                    booking.userId
                }
                else -> null
            }

            if (targetUid != null) {
                repository.fetchUser(targetUid, object : DataRepository.DataCallback<UserModel> {
                    override fun onSuccess(data: UserModel?) {
                        item.userModel = data
                        notifyUpdate(item) // Update UI immediately with user name/image
                        
                        // Check if returning client
                        val currentCaId = currentUserId
                        if (currentCaId != null) {
                            repository.isReturningClient(currentCaId, targetUid, object : DataRepository.DataCallback<Boolean> {
                                override fun onSuccess(isReturning: Boolean?) {
                                    item.isReturning = isReturning ?: false
                                    notifyUpdate(item)
                                }
                                override fun onError(error: String?) {
                                    notifyUpdate(item)
                                }
                            })
                        } else {
                            notifyUpdate(item)
                        }
                    }

                    override fun onError(error: String?) {
                        android.util.Log.e("RequestsActivity", "Failed to fetch user data for ${targetUid}: $error")
                        item.userModel = null
                        notifyUpdate(item)
                    }
                })
            }
        }
    }

    private fun notifyUpdate(item: RequestItem) {
        runOnUiThread {
            val currentPos = allRequests.indexOf(item)
            if (currentPos != -1) {
                adapter.notifyItemChanged(currentPos)
            }
        }
    }

    private fun updateAdapter() {
        adapter.setItems(allRequests)
    }

    override fun onAccept(item: RequestItem) {
        when (item.type) {
            RequestItem.TYPE_CONVERSATION -> {
                val request = item.data as ConversationModel
                repository.acceptRequest(request, object : DataRepository.DataCallback<Void?> {
                    override fun onSuccess(data: Void?) {
                        showToast("Request Accepted")
                        allRequests.remove(item)
                        updateAdapter()
                    }

                    override fun onError(error: String?) {
                        showToast("Failed to accept: $error")
                    }
                })
            }
            RequestItem.TYPE_BOOKING -> {
                val booking = item.data as BookingModel
                val bookingId = booking.id ?: return
                repository.updateBookingStatus(bookingId, "ACCEPTED", object : DataRepository.DataCallback<Void?> {
                    override fun onSuccess(data: Void?) {
                        showToast(getString(R.string.booking_accepted))
                        val caId = booking.caId
                        val userId = booking.userId
                        if (caId != null && userId != null) {
                            repository.incrementClientCount(caId, userId)
                        }
                        allRequests.remove(item)
                        updateAdapter()
                    }

                    override fun onError(error: String?) {
                        showToast(getString(R.string.failed_to_accept_booking, error))
                    }
                })
            }
        }
    }

    override fun onReject(item: RequestItem) {
        when (item.type) {
            RequestItem.TYPE_CONVERSATION -> {
                val request = item.data as ConversationModel
                val convId = request.conversationId ?: return
                repository.updateConversationState(convId, ConversationModel.STATE_REFUSED, object : DataRepository.DataCallback<Void?> {
                    override fun onSuccess(data: Void?) {
                        showToast(getString(R.string.request_refused))
                        allRequests.remove(item)
                        updateAdapter()
                    }

                    override fun onError(error: String?) {
                        showToast(getString(R.string.failed_to_refuse, error))
                    }
                })
            }
            RequestItem.TYPE_BOOKING -> {
                val booking = item.data as BookingModel
                val bookingId = booking.id ?: return
                repository.updateBookingStatus(bookingId, "REJECTED", object : DataRepository.DataCallback<Void?> {
                    override fun onSuccess(data: Void?) {
                        showToast(getString(R.string.booking_rejected))
                        allRequests.remove(item)
                        updateAdapter()
                    }

                    override fun onError(error: String?) {
                        showToast(getString(R.string.failed_to_reject_booking, error))
                    }
                })
            }
        }
    }
}
