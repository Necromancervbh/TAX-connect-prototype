package com.example.taxconnect.features.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taxconnect.R
import android.view.LayoutInflater
import android.graphics.Color
import com.example.taxconnect.core.base.BaseActivity
import com.example.taxconnect.core.utils.TaxDeadlineHelper
import com.example.taxconnect.features.ca.CADetailActivity
import com.example.taxconnect.features.community.CommunityActivity
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.features.ca.ExploreCAsActivity
import com.example.taxconnect.features.home.MainActivity
import com.example.taxconnect.features.booking.MyBookingsActivity
import com.example.taxconnect.features.notification.NotificationSettingsActivity
import com.example.taxconnect.features.notification.NotificationHistoryActivity
import com.example.taxconnect.features.documents.MyDocumentsActivity
import com.example.taxconnect.features.chat.MyChatsActivity
import com.example.taxconnect.features.profile.ProfileActivity
import com.example.taxconnect.features.wallet.WalletActivity
import com.example.taxconnect.features.ca.CAAdapter
import com.example.taxconnect.databinding.ActivityHomeBinding
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.common.Resource
import com.example.taxconnect.core.utils.ListUtils
import com.example.taxconnect.core.ui.ThemeHelper
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : BaseActivity<ActivityHomeBinding>(), CAAdapter.OnRequestAssistanceClickListener, CAAdapter.OnSaveClickListener {

    override val bindingInflater: (LayoutInflater) -> ActivityHomeBinding = ActivityHomeBinding::inflate
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: CAAdapter
    private lateinit var featuredAdapter: CAAdapter
    private val fullCAList = ArrayList<UserModel>()
    private val favoriteIds = HashSet<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            // Inform user that that your app will not show notifications.
        }
    }

    private var isFabExpanded = false

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                binding.etSearch.setText(spokenText)
                binding.etSearch.setSelection(spokenText.length)
                // Filter list based on spoken text
                filterCAs(spokenText)
            }
        }
    }

    override fun initViews() {
        try {
            setupRecyclerView()
            setupDrawer()
            setupFab()
            setupBottomNavigation()

            viewModel.fetchCAs()
            loadFavorites()
            loadMyProfessionals()
            
            askNotificationPermission()
            updateFcmToken()
            setupProfileCompletion()
            setupTaxDeadlines()
        } catch (e: Exception) {
            android.util.Log.e("HomeActivity", "Error in initViews", e)
            showToast("Error loading Home: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    override fun observeViewModel() {
        setupObservers()
    }

    private fun setupFab() {
        binding.fabMain.setOnClickListener {
            toggleFab()
        }

        binding.fabOverlay.setOnClickListener {
            if (isFabExpanded) toggleFab()
        }

        binding.fabSearch.setOnClickListener {
            startActivity(Intent(this, ExploreCAsActivity::class.java))
            toggleFab()
        }

        binding.fabBookings.setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
            toggleFab()
        }
    }

    private fun toggleFab() {
        isFabExpanded = !isFabExpanded
        
        val duration = 200L
        val rotation = if (isFabExpanded) 45f else 0f
        val alpha = if (isFabExpanded) 1f else 0f
        
        binding.fabMain.animate()
            .rotation(rotation)
            .setDuration(duration)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()

        if (isFabExpanded) {
            binding.fabOverlay.visibility = View.VISIBLE
            binding.fabOverlay.animate()
                .alpha(1f)
                .setDuration(duration)
                .start()
            
            expandSubFab(binding.layoutFabSearch, 1)
            expandSubFab(binding.layoutFabBookings, 2)
        } else {
            binding.fabOverlay.animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    binding.fabOverlay.visibility = View.GONE
                }.start()
            
            collapseSubFab(binding.layoutFabSearch)
            collapseSubFab(binding.layoutFabBookings)
        }
    }

    private fun expandSubFab(view: View, index: Int) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = 20f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .setStartDelay((index * 30).toLong())
            .withLayer()
            .start()
    }

    private fun collapseSubFab(view: View) {
        view.animate()
            .alpha(0f)
            .translationY(20f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withLayer()
            .withEndAction {
                view.visibility = View.GONE
            }
            .start()
    }

    private fun setupProfileCompletion() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        DataRepository.getInstance().fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(user: UserModel?) {
                if (user == null) return
                
                var completedFields = 0
                val totalFields = 6
                
                if (!user.name.isNullOrBlank()) completedFields++
                if (!user.email.isNullOrBlank()) completedFields++
                if (!user.phoneNumber.isNullOrBlank()) completedFields++
                if (!user.city.isNullOrBlank()) completedFields++
                if (!user.specialization.isNullOrBlank()) completedFields++
                if (!user.profileImageUrl.isNullOrBlank()) completedFields++
                
                val percentage = (completedFields * 100) / totalFields
                
                if (percentage < 100) {
                    binding.cardProfileCompletion.visibility = View.VISIBLE
                    binding.profileProgress.progress = percentage
                    binding.tvProfilePercentage.text = "$percentage%"
                    
                    val missingField = when {
                        user.profileImageUrl.isNullOrBlank() -> "profile picture"
                        user.city.isNullOrBlank() -> "city"
                        user.specialization.isNullOrBlank() -> "specialization"
                        user.phoneNumber.isNullOrBlank() -> "phone number"
                        else -> "profile details"
                    }
                    binding.tvProfileCompletionDesc.text = "Add your $missingField to complete your profile."
                    
                    binding.btnCompleteProfile.setOnClickListener {
                        // EditProfileActivity is expected to be defined/imported elsewhere
                        startActivity(Intent(this@HomeActivity, ProfileActivity::class.java))
                    }
                } else {
                    binding.cardProfileCompletion.visibility = View.GONE
                }
            }

            override fun onError(error: String?) {
                handleError(error)
            }
        })
    }

    private fun setupTaxDeadlines() {
        val container = binding.layoutDeadlines
        container.removeAllViews()
        val deadlines = TaxDeadlineHelper.getUpcomingDeadlines(5)
        val inflater = LayoutInflater.from(this)
        for (deadline in deadlines) {
            val card = inflater.inflate(R.layout.item_tax_deadline, container, false)
            card.findViewById<TextView>(R.id.tvDeadlineTitle).text = deadline.title
            card.findViewById<TextView>(R.id.tvDeadlineDate).text =
                TaxDeadlineHelper.formatDate(deadline)
            card.findViewById<TextView>(R.id.tvDeadlineDesc).text = deadline.description
            val days = TaxDeadlineHelper.daysUntil(deadline)
            card.findViewById<TextView>(R.id.tvDeadlineDaysLeft).text = when {
                days == 0 -> "Today!"
                days == 1 -> "Tomorrow"
                days < 0 -> "Passed"
                else -> "in $days days"
            }
            try {
                val color = android.graphics.Color.parseColor(deadline.colorHex)
                card.findViewById<View>(R.id.viewDeadlineStripe).setBackgroundColor(color)
                card.findViewById<TextView>(R.id.tvDeadlineDate).setTextColor(color)
            } catch (_: Exception) {}
            container.addView(card)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.caListState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.shimmerTopRated.visibility = View.VISIBLE
                        binding.shimmerFeatured.visibility = View.VISIBLE
                        binding.rvTopRated.visibility = View.GONE
                        binding.rvFeaturedCAs.visibility = View.GONE
                    }
                    is Resource.Success -> {
                        binding.shimmerTopRated.visibility = View.GONE
                        binding.shimmerFeatured.visibility = View.GONE
                        binding.rvTopRated.visibility = View.VISIBLE
                        binding.rvFeaturedCAs.visibility = View.VISIBLE
                        val caList = resource.data ?: emptyList()
                        val caOnlyList = caList.filter { it.role == "CA" }
                        fullCAList.clear()
                        fullCAList.addAll(caList) // Keep original for reference, or filter here

                        val result = ListUtils.processCALists(caOnlyList)
                        val topList = result.topList
                        val featuredList = result.featuredList

                        adapter.updateList(topList)
                        featuredAdapter.updateList(featuredList)

                        if (featuredList.isEmpty()) {
                            binding.layoutFeatured.visibility = View.GONE
                            binding.rvFeaturedCAs.visibility = View.GONE
                        } else {
                            binding.layoutFeatured.visibility = View.VISIBLE
                            binding.rvFeaturedCAs.visibility = View.VISIBLE
                        }

                        if (topList.isEmpty()) {
                            binding.layoutEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyState.text = getString(R.string.no_cas_available)
                        } else {
                            binding.layoutEmptyState.visibility = View.GONE
                        }
                    }
                    is Resource.Error -> {
                        binding.shimmerTopRated.visibility = View.GONE
                        binding.shimmerFeatured.visibility = View.GONE
                        handleError(resource.message) { viewModel.fetchCAs() }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.userState.collect { resource ->
                if (resource is Resource.Success) {
                    val user = resource.data
                    if (user != null) {
                        updateNavHeaderUI(user)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.unreadCountState.collect { count ->
                val badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_chats)
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count
                } else {
                    badge.isVisible = false
                }
            }
        }
    }



    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Search for CAs, specializations, or cities...")
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            showToast("Voice search not supported on this device")
        }
    }

    private fun filterCAs(query: String) {
        val caOnlyList = fullCAList.filter { it.role == "CA" }
        if (query.isEmpty()) {
            val result = ListUtils.processCALists(caOnlyList)
            adapter.updateList(result.topList)
            featuredAdapter.updateList(result.featuredList)
            binding.layoutEmptyState.visibility = if (caOnlyList.isEmpty()) View.VISIBLE else View.GONE
            return
        }

        val filtered = caOnlyList.filter { ca ->
            (ca.name?.contains(query, ignoreCase = true) == true) ||
            (ca.specialization?.contains(query, ignoreCase = true) == true) ||
            (ca.city?.contains(query, ignoreCase = true) == true)
        }

        val result = ListUtils.processCALists(filtered as ArrayList<UserModel>)
        adapter.updateList(result.topList)
        featuredAdapter.updateList(result.featuredList)

        if (result.topList.isEmpty() && result.featuredList.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = "No CAs found matching \"$query\""
        } else {
            binding.layoutEmptyState.visibility = View.GONE
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val view = findViewById<View>(item.itemId)
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_explore -> {
                    startActivity(Intent(this, ExploreCAsActivity::class.java))
                    false
                }
                R.id.nav_chats -> {
                    startActivity(Intent(this, MyChatsActivity::class.java))
                    false
                }
                R.id.nav_notifications -> {
                    val intent = Intent(this, NotificationHistoryActivity::class.java)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }

        // Badges are now handled by observer
    }

    override fun setupListeners() {
        binding.ivVoiceSearch.setOnClickListener {
            startVoiceSearch()
        }

        binding.ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.ivMyChats.setOnClickListener {
            startActivity(Intent(this, MyChatsActivity::class.java))
        }

        binding.ivNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }

        binding.btnViewAll.setOnClickListener {
            startActivity(Intent(this, ExploreCAsActivity::class.java))
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCAs(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = textView.text.toString()
                val intent = Intent(this, ExploreCAsActivity::class.java)
                intent.putExtra("QUERY", query)
                startActivity(intent)
                true
            } else {
                false
            }
        }

        binding.chipExplore.setOnClickListener {
            startActivity(Intent(this, ExploreCAsActivity::class.java))
        }

        binding.chipBookings.setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }

        binding.chipWallet.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }

        binding.chipSaved.setOnClickListener {
            val intent = Intent(this, ExploreCAsActivity::class.java)
            intent.putExtra("FAVORITES_ONLY", true)
            startActivity(intent)
        }
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token != null) {
                    DataRepository.getInstance().updateFcmToken(token)
                }
            }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Granted
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupRecyclerView() {
        // Setup Top Rated
        val lm = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvTopRated.layoutManager = lm
        adapter = CAAdapter(ArrayList(), this, true)
        adapter.setOnSaveClickListener(this)
        binding.rvTopRated.adapter = adapter
        LinearSnapHelper().attachToRecyclerView(binding.rvTopRated)

        binding.rvTopRated.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                applyCenterScale(recyclerView)
            }
        })

        binding.rvTopRated.post { applyCenterScale(binding.rvTopRated) }

        // Setup Featured
        val lmFeatured = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFeaturedCAs.layoutManager = lmFeatured
        featuredAdapter = CAAdapter(ArrayList(), this, true)
        featuredAdapter.setOnSaveClickListener(this)
        binding.rvFeaturedCAs.adapter = featuredAdapter
        LinearSnapHelper().attachToRecyclerView(binding.rvFeaturedCAs)

        binding.rvFeaturedCAs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                applyCenterScale(recyclerView)
            }
        })

        binding.rvFeaturedCAs.post { applyCenterScale(binding.rvFeaturedCAs) }
    }

    private fun applyCenterScale(recyclerView: RecyclerView) {
        val centerX = recyclerView.width / 2
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val childCenterX = (child.left + child.right) / 2
            val distance = abs(centerX - childCenterX)
            val maxScale = 1.0f
            val minScale = 0.9f
            val maxDistance = recyclerView.width / 2
            val scale = maxScale - (min(distance.toFloat(), maxDistance.toFloat()) / maxDistance.toFloat()) * (maxScale - minScale)
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    private fun setupDrawer() {
        binding.ivMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on Home
                }
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_chats -> startActivity(Intent(this, MyChatsActivity::class.java))
                R.id.nav_notifications -> startActivity(Intent(this, NotificationHistoryActivity::class.java))
                R.id.nav_wallet -> startActivity(Intent(this, WalletActivity::class.java))
                R.id.nav_bookings -> startActivity(Intent(this, MyBookingsActivity::class.java))
                R.id.nav_docs -> startActivity(Intent(this, MyDocumentsActivity::class.java))
                R.id.nav_community -> startActivity(Intent(this, CommunityActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, NotificationSettingsActivity::class.java))
                R.id.nav_help -> showFeedbackDialog()
                R.id.nav_explore -> startActivity(Intent(this, ExploreCAsActivity::class.java))
                R.id.nav_logout -> {
                    val uid = FirebaseAuth.getInstance().uid
                    if (uid != null) {
                        DataRepository.getInstance().clearUserCache(uid)
                    }
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
    }

    private fun updateNavHeader() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid != null) {
            viewModel.fetchUser(uid)
        }
    }

    private fun updateNavHeaderUI(user: UserModel) {
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
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(it)
            }
        }

        Glide.with(this)
            .load(user.profileImageUrl)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .circleCrop()
            .into(binding.ivProfile)
    }

    override fun onRequestAssistanceClick(ca: UserModel) {
        val intent = Intent(this, CADetailActivity::class.java)
        intent.putExtra("CA_DATA", ca)
        startActivity(intent)
    }

    override fun onSaveClick(ca: UserModel) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val caId = ca.uid ?: return
        
        DataRepository.getInstance().toggleFavorite(uid, caId, object : DataRepository.DataCallback<Boolean> {
            override fun onSuccess(isFav: Boolean?) {
                if (isFav == true) {
                    favoriteIds.add(caId)
                    showToast("CA Profile Saved")
                } else {
                    favoriteIds.remove(caId)
                    showToast("CA Profile Removed")
                }
                adapter.setFavorites(favoriteIds)
                featuredAdapter.setFavorites(favoriteIds)
            }

            override fun onError(error: String?) {
                handleError(error)
            }
        })
    }

    private fun loadFavorites() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        DataRepository.getInstance().getFavoriteCaIds(uid, object : DataRepository.DataCallback<List<String>> {
            override fun onSuccess(ids: List<String>?) {
                if (ids != null) {
                    favoriteIds.clear()
                    favoriteIds.addAll(ids)
                    adapter.setFavorites(favoriteIds)
                    featuredAdapter.setFavorites(favoriteIds)
                }
            }

            override fun onError(error: String?) {
                // Ignore
            }
        })
    }

    private fun loadMyProfessionals() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("bookings")
            .whereEqualTo("userId", uid)
            .whereIn("status", listOf("ACCEPTED", "COMPLETED", "CONFIRMED"))
            .limit(20)
            .get()
            .addOnSuccessListener { snap ->
                val caIds = snap.documents.mapNotNull { it.getString("caId") }.distinct().take(10)
                if (caIds.isEmpty()) return@addOnSuccessListener
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val caProfiles = mutableListOf<UserModel>()
                var fetched = 0
                caIds.forEach { caId ->
                    db.collection("users").document(caId).get().addOnSuccessListener { doc ->
                        fetched++
                        doc.toObject(UserModel::class.java)?.let { caProfiles.add(it) }
                        if (fetched == caIds.size && caProfiles.isNotEmpty()) {
                            runOnUiThread {
                                binding.layoutMyProfessionals.visibility = View.VISIBLE
                                binding.rvMyProfessionals.visibility = View.VISIBLE
                                val profAdapter = CAAdapter(caProfiles, this@HomeActivity, true)
                                binding.rvMyProfessionals.layoutManager =
                                    androidx.recyclerview.widget.LinearLayoutManager(
                                        this@HomeActivity,
                                        androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
                                    )
                                binding.rvMyProfessionals.adapter = profAdapter
                                binding.btnViewAllProfessionals.setOnClickListener {
                                    startActivity(Intent(this@HomeActivity, MyBookingsActivity::class.java))
                                }
                            }
                        }
                    }
                }
            }
    }
}
