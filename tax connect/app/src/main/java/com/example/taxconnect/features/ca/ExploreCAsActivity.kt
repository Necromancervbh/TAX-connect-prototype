package com.example.taxconnect.features.ca

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.*
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taxconnect.R
import com.example.taxconnect.features.ca.CAAdapter
import com.example.taxconnect.databinding.ActivityExploreCasBinding
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.DataRepository
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import java.util.*
import java.util.stream.Collectors
import com.example.taxconnect.core.base.BaseActivity

import com.example.taxconnect.core.navigation.NavigationManager

class ExploreCAsActivity : BaseActivity<ActivityExploreCasBinding>(), CAAdapter.OnRequestAssistanceClickListener {
    override val bindingInflater: (LayoutInflater) -> ActivityExploreCasBinding = ActivityExploreCasBinding::inflate
    private val navigationManager = NavigationManager()
    private lateinit var repository: DataRepository
    private lateinit var adapter: CAAdapter
    private var fullCAList = ArrayList<UserModel>()
    private var filterMinPrice = 0.0
    private var filterMaxPrice = Double.MAX_VALUE
    private var currentSearchQuery = ""
    private var filterVerifiedOnly = false
    private var filterCityOnly = false
    private var favoritesOnly = false
    private var currentUserCity: String? = null
    private var favoriteIds = HashSet<String>()
    private var lastCaSnapshot: DocumentSnapshot? = null
    private var isLoadingCAs = false
    private var hasMoreCAs = true
    private val CA_PAGE_SIZE = 30

    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { applySearch(currentSearchQuery) }
    private val prefs by lazy { getSharedPreferences("explore_prefs", MODE_PRIVATE) }

    companion object {
        private const val PREF_RECENT_SEARCHES = "recent_searches"
    }

    override fun initViews() {
        try {
            repository = DataRepository.getInstance()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to initialize data repository")
            showToast("Failed to initialize data repository")
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        setupSorting()
        setupSearch()
        renderRecentChips(loadRecentSearches())
        showLoading(true)
        fetchCAList()
        loadFavorites()
        loadCurrentUserCity()

        val initialQuery = intent.getStringExtra("QUERY")
        if (!initialQuery.isNullOrEmpty()) {
            binding.etSearch.setText(initialQuery)
            binding.etSearch.setSelection(initialQuery.length)
            saveRecentSearch(initialQuery)
            renderRecentChips(loadRecentSearches())
        }
        favoritesOnly = intent.getBooleanExtra("FAVORITES_ONLY", false)
        binding.chipFavorites.isChecked = favoritesOnly
    }

    override fun observeViewModel() {
        // No ViewModel used in this activity currently
    }

    override fun setupListeners() {
        // Listeners are set up in setupUI
    }

    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }
        binding.btnFilter.setOnClickListener { showPriceFilterDialog() }
        binding.chipVerified.setOnCheckedChangeListener { _, isChecked ->
            filterVerifiedOnly = isChecked
            filterList(currentSearchQuery)
        }
        binding.chipFavorites.setOnCheckedChangeListener { _, isChecked ->
            favoritesOnly = isChecked
            filterList(currentSearchQuery)
        }
        binding.chipMyCity.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked && currentUserCity.isNullOrBlank()) {
                buttonView.isChecked = false
                showToast(getString(R.string.add_city_profile_filter))
                return@setOnCheckedChangeListener
            }
            filterCityOnly = isChecked
            filterList(currentSearchQuery)
        }
    }

    private fun setupRecyclerView() {
        adapter = CAAdapter(ArrayList(), this, this, false)
        binding.rvCAList.layoutManager = LinearLayoutManager(this)
        binding.rvCAList.adapter = adapter
        binding.rvCAList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val total = adapter.itemCount
                if (!isLoadingCAs && hasMoreCAs && lastVisible >= total - 5) {
                    loadMoreCAs(false)
                }
            }
        })
        adapter.setOnSaveClickListener(object : CAAdapter.OnSaveClickListener {
            override fun onSaveClick(ca: UserModel) {
                val uid = FirebaseAuth.getInstance().uid ?: return
                repository.toggleFavorite(uid, ca.uid ?: return, object : DataRepository.DataCallback<Boolean> {
                    override fun onSuccess(isFav: Boolean?) {
                        if (isFav == true) {
                            favoriteIds.add(ca.uid!!)
                        } else {
                            favoriteIds.remove(ca.uid!!)
                        }
                        adapter.setFavorites(favoriteIds)
                    }

                    override fun onError(error: String?) {}
                })
            }
        })
    }

    private fun showPriceFilterDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.filter_by_price))

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 16, 32, 16)

        val inputMin = EditText(this)
        inputMin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        inputMin.hint = "Min Price (₹)"
        if (filterMinPrice > 0) inputMin.setText(filterMinPrice.toString())
        layout.addView(inputMin)

        val inputMax = EditText(this)
        inputMax.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        inputMax.hint = "Max Price (₹)"
        if (filterMaxPrice < Double.MAX_VALUE) inputMax.setText(filterMaxPrice.toString())
        layout.addView(inputMax)

        builder.setView(layout)

        builder.setPositiveButton(getString(R.string.apply)) { _, _ ->
            val minStr = inputMin.text.toString()
            val maxStr = inputMax.text.toString()

            filterMinPrice = minStr.toDoubleOrNull() ?: 0.0
            filterMaxPrice = maxStr.toDoubleOrNull() ?: Double.MAX_VALUE

            filterList(currentSearchQuery)
        }

        builder.setNegativeButton(getString(R.string.clear)) { dialog, _ ->
            filterMinPrice = 0.0
            filterMaxPrice = Double.MAX_VALUE
            filterList(currentSearchQuery)
            dialog.cancel()
        }

        builder.setNeutralButton(getString(R.string.cancel)) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun setupSorting() {
        val sortOptions = arrayOf("Rating (High to Low)", "Experience (High to Low)", "Charges (Low to High)")
        val sortAdapter = ArrayAdapter(this, R.layout.item_spinner, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = sortAdapter

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortList(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                debounceSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchCAList() {
        fullCAList.clear()
        lastCaSnapshot = null
        hasMoreCAs = true
        loadMoreCAs(true)
    }

    private fun loadMoreCAs(initialLoad: Boolean) {
        if (isLoadingCAs || !hasMoreCAs) return
        isLoadingCAs = true
        if (initialLoad) {
            showLoading(true)
        }

        repository.getCaPage(CA_PAGE_SIZE, lastCaSnapshot, object : DataRepository.DataCallback<DataRepository.PageResult<UserModel>> {
            override fun onSuccess(data: DataRepository.PageResult<UserModel>?) {
                if (initialLoad) showLoading(false)
                if (data == null) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvCAList.visibility = View.GONE
                    binding.tvRegisteredCount.text = getString(R.string.registered_cas_count)
                    isLoadingCAs = false
                    return
                }

                val newItems = data.items
                if (newItems.isEmpty()) {
                    hasMoreCAs = false
                } else {
                    fullCAList.addAll(newItems)
                    lastCaSnapshot = data.lastSnapshot
                }

                val fullText = getString(R.string.registered_cas_count_dynamic, fullCAList.size)
                val countText = fullCAList.size.toString()
                val spannable = SpannableString(fullText)
                val startIndex = fullText.indexOf(countText)
                if (startIndex != -1) {
                    spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + countText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                binding.tvRegisteredCount.text = spannable

                if (fullCAList.isEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvCAList.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvCAList.visibility = View.VISIBLE
                    filterList(currentSearchQuery)
                }
                isLoadingCAs = false
            }

            override fun onError(error: String?) {
                if (initialLoad) showLoading(false)
                isLoadingCAs = false
                handleError(error) { loadMoreCAs(initialLoad) }
            }
        })
    }

    private fun filterList(query: String) {
        currentSearchQuery = query
        val filtered = fullCAList.filter { ca ->
            val matchesQuery = query.isEmpty() ||
                    (ca.name?.contains(query, ignoreCase = true) == true) ||
                    (ca.specialization?.contains(query, ignoreCase = true) == true) ||
                    (ca.city?.contains(query, ignoreCase = true) == true)

            var matchesPrice = true
            ca.minCharges?.let { charges ->
                val price = parseCharges(charges)
                if (price != Double.MAX_VALUE) {
                    matchesPrice = price in filterMinPrice..filterMaxPrice
                }
            }

            val matchesVerified = !filterVerifiedOnly || ca.isVerified
            val matchesFavorites = !favoritesOnly || favoriteIds.contains(ca.uid)
            val matchesCity = !filterCityOnly || (currentUserCity != null && ca.city?.equals(currentUserCity, ignoreCase = true) == true)
            val matchesRole = ca.role == "CA"

            matchesQuery && matchesPrice && matchesVerified && matchesFavorites && matchesCity && matchesRole
        }

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvCAList.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvCAList.visibility = View.VISIBLE
            adapter.updateList(filtered)
        }
    }

    private fun sortList(position: Int) {
        if (fullCAList.isEmpty()) return

        when (position) {
            0 -> fullCAList.sortByDescending { it.rating }
            1 -> fullCAList.sortByDescending { parseExperience(it.experience) }
            2 -> fullCAList.sortBy { parseCharges(it.minCharges) }
        }
        filterList(currentSearchQuery)
    }

    private fun parseExperience(exp: String?): Double {
        if (exp == null) return 0.0
        return try {
            exp.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseCharges(charge: String?): Double {
        if (charge == null) return Double.MAX_VALUE
        return try {
            charge.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: Double.MAX_VALUE
        } catch (e: Exception) {
            Double.MAX_VALUE
        }
    }

    private fun debounceSearch() {
        searchHandler.removeCallbacks(searchRunnable)
        searchHandler.postDelayed(searchRunnable, 300)
    }

    private fun applySearch(query: String) {
        filterList(query)
        if (query.trim().length >= 2) {
            saveRecentSearch(query.trim())
            renderRecentChips(loadRecentSearches())
        }
    }

    private fun loadRecentSearches(): List<String> {
        val csv = prefs.getString(PREF_RECENT_SEARCHES, "") ?: ""
        return if (csv.isEmpty()) emptyList() else csv.split("|").filter { it.isNotBlank() }
    }

    private fun saveRecentSearch(query: String) {
        val current = loadRecentSearches().toMutableList()
        current.remove(query)
        current.add(0, query)
        val ordered = if (current.size > 5) current.take(5) else current
        prefs.edit().putString(PREF_RECENT_SEARCHES, ordered.joinToString("|")).apply()
    }

    private fun clearRecentSearches() {
        prefs.edit().remove(PREF_RECENT_SEARCHES).apply()
        renderRecentChips(emptyList())
    }

    private fun renderRecentChips(items: List<String>) {
        binding.chipGroupRecent.removeAllViews()
        binding.hsRecentSearches.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        for (label in items) {
            val chip = Chip(this)
            chip.text = label
            chip.isCheckable = false
            chip.isClickable = true
            chip.setOnClickListener {
                binding.etSearch.setText(label)
                binding.etSearch.setSelection(label.length)
                applySearch(label)
            }
            binding.chipGroupRecent.addView(chip)
        }
        if (items.isNotEmpty()) {
            val clearChip = Chip(this)
            clearChip.text = getString(R.string.clear)
            clearChip.isCheckable = false
            clearChip.isClickable = true
            clearChip.setOnClickListener { clearRecentSearches() }
            binding.chipGroupRecent.addView(clearChip)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.shimmerLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvCAList.visibility = if (show) View.GONE else View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
    }

    private fun loadFavorites() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        repository.getFavoriteCaIds(uid, object : DataRepository.DataCallback<List<String>> {
            override fun onSuccess(data: List<String>?) {
                if (data != null) {
                    favoriteIds.clear()
                    favoriteIds.addAll(data)
                    adapter.setFavorites(favoriteIds)
                    if (favoritesOnly) {
                        filterList(currentSearchQuery)
                    }
                }
            }

            override fun onError(error: String?) {}
        })
    }

    private fun loadCurrentUserCity() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        repository.fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                data?.let {
                    currentUserCity = it.city
                    val hasCity = !currentUserCity.isNullOrBlank()
                    binding.chipMyCity.isEnabled = hasCity
                    binding.chipMyCity.visibility = if (hasCity) View.VISIBLE else View.GONE
                }
            }

            override fun onError(error: String?) {}
        })
    }

    override fun onRequestAssistanceClick(ca: UserModel) {
        navigationManager.navigateToCADetail(this, ca, true)
    }
}
