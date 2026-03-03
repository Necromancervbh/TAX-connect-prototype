package com.example.taxconnect.features.ca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import com.example.taxconnect.features.ca.RatingAdapter
import com.example.taxconnect.features.ca.ServiceAdapter
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.features.auth.LoginActivity
import com.example.taxconnect.features.documents.SecureDocViewerActivity
import com.example.taxconnect.databinding.ActivityCaDetailBinding
import com.example.taxconnect.data.models.CertificateModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.data.models.RatingModel
import com.example.taxconnect.data.models.ServiceModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.features.chat.ChatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class CADetailActivity : BaseActivity<ActivityCaDetailBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityCaDetailBinding = ActivityCaDetailBinding::inflate

    private var ca: UserModel? = null
    private var currentUser: UserModel? = null
    private var currentUserId: String? = null
    private var chatId: String? = null
    private var currentConversation: ConversationModel? = null
    private var conversationListener: ListenerRegistration? = null

    private lateinit var serviceAdapter: ServiceAdapter
    private lateinit var ratingAdapter: RatingAdapter
    private var isUserEligibleToRate = false

    override fun initViews() {
        currentUserId = FirebaseAuth.getInstance().uid
        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupRatingsRecyclerView()

        DataRepository.getInstance().fetchUser(currentUserId!!, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                currentUser = data
                checkRatingEligibility()
            }

            override fun onError(error: String?) {
                // Ignore
            }
        })

        if (intent.hasExtra("CA_DATA")) {
            ca = intent.getSerializableExtra("CA_DATA") as? UserModel
            if (ca != null) {
                // Initialize button state
                binding.btnConnect.text = getString(R.string.loading_dots)
                binding.btnConnect.isEnabled = false

                chatId = ConversationRepository.getInstance().getChatId(currentUserId!!, ca!!.uid!!)
                setupUI()
                setupServices()
                listenForConversationUpdates()
            } else {
                finish()
            }
        } else if (intent.hasExtra("userId")) {
            val userId = intent.getStringExtra("userId")
            if (userId != null) {
                // Show loading state
                binding.btnConnect.text = getString(R.string.loading_dots)
                binding.btnConnect.isEnabled = false
                
                DataRepository.getInstance().fetchUser(userId, object : DataRepository.DataCallback<UserModel> {
                    override fun onSuccess(data: UserModel?) {
                        if (data != null) {
                            ca = data
                            chatId = intent.getStringExtra("chatId") ?: ConversationRepository.getInstance().getChatId(currentUserId!!, ca!!.uid!!)
                            setupUI()
                            setupServices()
                            listenForConversationUpdates()
                        } else {
                            showToast("User not found", android.widget.Toast.LENGTH_SHORT)
                            finish()
                        }
                    }

                    override fun onError(error: String?) {
                        showToast("Error loading user: $error", android.widget.Toast.LENGTH_SHORT)
                        finish()
                    }
                })
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeViewModel() {
        // No ViewModel used in this activity yet
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationListener?.remove()
        conversationListener = null
    }

    private fun setupServices() {
        serviceAdapter = ServiceAdapter { service ->
            // Handle Buy Service
            showBuyServiceDialog(service)
        }
        binding.rvServices.layoutManager = LinearLayoutManager(this)
        binding.rvServices.adapter = serviceAdapter

        ca?.let { user ->
            DataRepository.getInstance().getServices(user.uid!!, object : DataRepository.DataCallback<List<ServiceModel>> {
                override fun onSuccess(data: List<ServiceModel>?) {
                    if (data != null) {
                        serviceAdapter.setServices(data)
                        if (data.isEmpty()) {
                            binding.rvServices.visibility = View.GONE
                            binding.tvServicesEmpty.visibility = View.VISIBLE
                        } else {
                            binding.rvServices.visibility = View.VISIBLE
                            binding.tvServicesEmpty.visibility = View.GONE
                        }
                    }
                }

                override fun onError(error: String?) {
                    // Ignore error, just hide section
                    binding.rvServices.visibility = View.GONE
                    binding.tvServicesEmpty.visibility = View.VISIBLE
                }
            })
        }
    }

    private fun showBuyServiceDialog(service: ServiceModel) {
        val intent = Intent(this, com.example.taxconnect.features.booking.BookAppointmentActivity::class.java)
        intent.putExtra("CA_DATA", ca)
        intent.putExtra("SELECTED_SERVICE_ID", service.id) // Optional: Pre-select service if supported
        startActivity(intent)
    }

    private fun initiatePayment(service: ServiceModel) {
        // Here we would integrate Razorpay
        // For now, simulate success

        val targetCa = ca ?: return
        val uid = currentUserId ?: return
        val cid = chatId ?: ConversationRepository.getInstance().getChatId(uid, targetCa.uid!!)

        // Create conversation request directly with payment info
        val request = ConversationModel()
        request.conversationId = cid
        request.participantIds = mutableListOf(uid, targetCa.uid!!)
        request.workflowState = ConversationModel.STATE_REQUESTED
        request.lastMessage = "Service Request: ${service.title}"
        request.lastMessageTimestamp = System.currentTimeMillis()

        ConversationRepository.getInstance().createConversation(request, object : DataRepository.DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                // Send initial message
                val msg = MessageModel(
                    uid, targetCa.uid!!, cid,
                    getString(R.string.service_purchase_message, service.title ?: ""),
                    System.currentTimeMillis(), "TEXT"
                )
                ConversationRepository.getInstance().sendMessage(msg, object : DataRepository.DataCallback<Void?> {
                    override fun onSuccess(data: Void?) {
                        showToast(getString(R.string.service_requested_success))
                    }

                    override fun onError(error: String?) {
                        // Still show success as conversation was created, message failure is minor
                        showToast(getString(R.string.service_requested_message_pending))
                    }
                })
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_request_service))
            }
        })
    }

    private fun setupUI() {
        val user = ca ?: return

        // Fetch latest CA data to ensure stats are up-to-date
        DataRepository.getInstance().fetchUser(user.uid!!, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(updatedCa: UserModel?) {
                if (updatedCa != null) {
                    ca = updatedCa
                    updateStatsUI()
                    setupCertificates()
                }
            }

            override fun onError(error: String?) {
                // Fallback to existing data
                updateStatsUI()
                setupCertificates()
            }
        })
    }

    private fun updateStatsUI() {
        val user = ca ?: return

        binding.tvName.text = user.name
        binding.tvSpecialization.text = "Specialization: ${user.specialization}"
        binding.tvExperience.text = "${user.experience} Years"
        binding.tvExperienceStat.text = "${user.experience}+"
        binding.tvClientStat.text = "${user.clientCount}+"
        binding.tvRatingStat.text = String.format(Locale.getDefault(), "%.1f", user.rating)
        binding.tvBio.text = if (user.bio.isNullOrBlank()) "This CA hasn't added a bio yet." else user.bio

        // Bind Pricing
        binding.tvMinCharges.text = if (!user.minCharges.isNullOrEmpty()) "₹ ${user.minCharges}" else "Contact for Price"

        // Bind Rating
        binding.tvRating.text = String.format(Locale.getDefault(), "%.1f (%d Reviews)", user.rating, user.ratingCount)
        loadRatings()

        // Verified Badge
        binding.ivVerifiedBadge.visibility = if (user.isVerified) View.VISIBLE else View.GONE

        // Load Profile Image
        if (!user.profileImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person_material3)
                .error(R.drawable.ic_person_material3)
                .into(binding.ivProfile)
        } else {
            binding.ivProfile.setImageResource(R.drawable.ic_person_material3)
        }

        // Setup Intro Video Button
        if (!user.introVideoUrl.isNullOrEmpty()) {
            binding.btnWatchIntro.visibility = View.VISIBLE
            binding.btnWatchIntro.setOnClickListener {
                showVideoBottomSheet(user.introVideoUrl!!)
            }
        } else {
            binding.btnWatchIntro.visibility = View.GONE
        }
    }

    private fun listenForConversationUpdates() {
        conversationListener?.remove()
        val cid = chatId ?: return

        conversationListener = ConversationRepository.getInstance().listenToConversation(cid, object : DataRepository.DataCallback<ConversationModel?> {
            override fun onSuccess(conversation: ConversationModel?) {
                currentConversation = conversation
                updateConnectButton()
            }

            override fun onError(error: String?) {
                // If error, assume no conversation or network issue.
                // If we treat it as null, user can try to request again.
                currentConversation = null
                updateConnectButton()
            }
        })
    }

    private fun updateConnectButton() {
        binding.btnConnect.isEnabled = true
        binding.tvRequestStatus.visibility = View.GONE

        if (currentConversation == null || ConversationModel.STATE_REFUSED == currentConversation?.workflowState) {
            binding.btnConnect.text = getString(R.string.request_assistance)
            binding.btnConnect.setOnClickListener { 
                val intent = Intent(this, com.example.taxconnect.features.booking.BookAppointmentActivity::class.java)
                intent.putExtra("CA_DATA", ca)
                startActivity(intent)
            }

        } else if (ConversationModel.STATE_REQUESTED == currentConversation?.workflowState) {
            binding.btnConnect.text = getString(R.string.request_pending)
            binding.btnConnect.setOnClickListener {
                showToast(getString(R.string.request_pending_approval_msg))
            }
            binding.tvRequestStatus.text = getString(R.string.request_pending_approval)
            binding.tvRequestStatus.visibility = View.VISIBLE

        } else {
            // Discussion, Accepted, etc.
            binding.btnConnect.text = getString(R.string.message)
            binding.btnConnect.setOnClickListener {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("chatId", chatId)
                intent.putExtra("otherUserId", ca?.uid)
                intent.putExtra("otherUserName", ca?.name)
                startActivity(intent)
            }
        }
    }

    private fun showRequestDialog() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_send_request, null)
        builder.setView(view)
        val dialog = builder.create()

        val etMessage = view.findViewById<TextInputEditText>(R.id.etRequestMessage)
        val btnSend = view.findViewById<Button>(R.id.btnSendRequest)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelRequest)

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isEmpty()) {
                showToast(getString(R.string.please_enter_query))
                return@setOnClickListener
            }

            sendRequest(message, dialog)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun sendRequest(message: String, dialog: AlertDialog) {
        val uid = currentUserId ?: return
        val targetCa = ca ?: return

        ConversationRepository.getInstance().sendRequest(uid, targetCa.uid!!, message, object : DataRepository.DataCallback<Void?> {
            override fun onSuccess(data: Void?) {
                showToast(getString(R.string.request_sent_success))
                dialog.dismiss()
                // Button update will happen automatically via listener
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.failed_to_send_request, error))
            }
        })
    }

    private fun showVideoBottomSheet(videoUrl: String) {
        if (videoUrl.isEmpty()) return

        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_video_bottom_sheet, null)
        bottomSheetDialog.setContentView(view)

        val playerView = view.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        playerView.player = player

        // Prepare the player
        val mediaItem = androidx.media3.common.MediaItem.fromUri(Uri.parse(videoUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        bottomSheetDialog.setOnDismissListener {
            player.stop()
            player.release()
        }

        btnClose.setOnClickListener { bottomSheetDialog.dismiss() }

        bottomSheetDialog.show()
    }

    private fun setupRatingsRecyclerView() {
        ratingAdapter = RatingAdapter()
        binding.rvRatings.layoutManager = LinearLayoutManager(this)
        binding.rvRatings.adapter = ratingAdapter

        binding.btnRateCa.setOnClickListener {
            val user = currentUser
            if (user != null && "CUSTOMER" != user.role) {
                showToast("Only customers can rate CAs.")
                return@setOnClickListener
            }
            if (!isUserEligibleToRate) {
                showToast("You can only review CAs after a completed service.")
                return@setOnClickListener
            }
            showRateDialog()
        }
    }

    private fun loadRatings() {
        val user = ca ?: return
        DataRepository.getInstance().getRatings(user.uid!!, object : DataRepository.DataCallback<List<RatingModel>> {
            override fun onSuccess(data: List<RatingModel>?) {
                if (data != null) {
                    ratingAdapter.setRatings(data)
                }
            }

            override fun onError(error: String?) {
                // Log or ignore
            }
        })
    }

    private fun checkRatingEligibility() {
        val user = ca ?: return
        val curUser = currentUser ?: return
        val uid = currentUserId ?: return

        // Only customers can rate
        if ("CUSTOMER" != curUser.role) {
            isUserEligibleToRate = false
            return
        }

        DataRepository.getInstance().checkRatingEligibility(uid, user.uid!!, object : DataRepository.DataCallback<Boolean> {
            override fun onSuccess(isEligible: Boolean?) {
                isUserEligibleToRate = isEligible ?: false
            }

            override fun onError(error: String?) {
                isUserEligibleToRate = false
            }
        })
    }

    private fun showRateDialog() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rate_ca, null)
        builder.setView(view)
        val dialog = builder.create()

        val ratingBar = view.findViewById<android.widget.RatingBar>(R.id.ratingBar)
        val etReview = view.findViewById<TextInputEditText>(R.id.etFeedback)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating
            val review = etReview.text.toString().trim()

            if (rating == 0f) {
                showToast(getString(R.string.please_select_rating))
                return@setOnClickListener
            }

            if (review.isNotEmpty()) {
                val words = review.split("\\s+".toRegex())
                if (words.size > 10) {
                    showToast(getString(R.string.review_limit_exceeded))
                    return@setOnClickListener
                }
            }

            val currentCa = ca ?: return@setOnClickListener
            val currentUserModel = currentUser ?: return@setOnClickListener

            val ratingModel = RatingModel(
                currentUserId,
                currentUserModel.name,
                currentCa.uid,
                rating,
                review,
                System.currentTimeMillis()
            )

            DataRepository.getInstance().addRating(ratingModel, object : DataRepository.DataCallback<Void?> {
                override fun onSuccess(data: Void?) {
                    showToast(getString(R.string.rating_submitted))
                    dialog.dismiss()
                    loadRatings()
                    refreshCAData() // Update top stats
                }

                override fun onError(error: String?) {
                    showToast(getString(R.string.failed_to_submit_rating, error))
                }
            })
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun refreshCAData() {
        val user = ca ?: return
        DataRepository.getInstance().fetchUser(user.uid!!, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(updatedCa: UserModel?) {
                ca = updatedCa
                setupUI() // Refresh UI with new stats
            }
            override fun onError(error: String?) {}
        })
    }

    private fun setupCertificates() {
        val user = ca ?: return
        if (!user.certificates.isNullOrEmpty()) {
            binding.tvCertificatesTitle.visibility = View.VISIBLE
            binding.rvCertificates.visibility = View.VISIBLE

            val adapter = CertificateViewerAdapter(user.certificates!!) { certificate ->
                val intent = Intent(this, SecureDocViewerActivity::class.java)
                intent.putExtra("DOC_URL", certificate.url)
                intent.putExtra("DOC_TYPE", certificate.type)
                intent.putExtra("DOC_TITLE", certificate.name)
                startActivity(intent)
            }
            binding.rvCertificates.layoutManager = LinearLayoutManager(this)
            binding.rvCertificates.adapter = adapter
        } else {
            binding.tvCertificatesTitle.visibility = View.GONE
            binding.rvCertificates.visibility = View.GONE
        }
    }

    private class CertificateViewerAdapter(
        private val certificates: List<CertificateModel>,
        private val listener: (CertificateModel) -> Unit
    ) : RecyclerView.Adapter<CertificateViewerAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_certificate_public, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cert = certificates[position]
            holder.tvName.text = cert.name
            holder.itemView.setOnClickListener { listener(cert) }
        }

        override fun getItemCount(): Int = certificates.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
        }
    }
}
