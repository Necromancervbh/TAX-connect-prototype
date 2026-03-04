package com.example.taxconnect.features.documents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.features.wallet.TransactionAdapter
import com.example.taxconnect.databinding.ActivityBalanceSheetBinding
import com.example.taxconnect.data.models.TransactionModel
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.core.base.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.*

class BalanceSheetActivity : BaseActivity<ActivityBalanceSheetBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityBalanceSheetBinding = ActivityBalanceSheetBinding::inflate

    private lateinit var repository: DataRepository
    private var currentUserId: String? = null
    private lateinit var adapter: TransactionAdapter
    private var isAuthenticated = false

    override fun initViews() {
        // Hide content initially
        binding.root.visibility = View.INVISIBLE

        repository = DataRepository.getInstance()
        currentUserId = FirebaseAuth.getInstance().uid

        authenticateUser()
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeViewModel() {
        // No ViewModel to observe yet
    }

    private fun authenticateUser() {
        // Biometric authentication removed
        isAuthenticated = true
        binding.root.visibility = View.VISIBLE
        initializeApp()
    }

    private fun initializeApp() {
        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter()
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun loadData() {
        val uid = currentUserId ?: return

        // Load Revenue Stats
        repository.getRevenueStats(uid, object : DataRepository.DataCallback<Double> {
            override fun onSuccess(totalRevenue: Double?) {
                binding.tvTotalEarnings.text = String.format(Locale.getDefault(), "₹ %.2f", totalRevenue ?: 0.0)
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.error_loading_revenue))
            }
        })

        // Load Transactions
        repository.getCaTransactions(uid, object : DataRepository.DataCallback<List<TransactionModel>> {
            override fun onSuccess(transactions: List<TransactionModel>?) {
                if (transactions != null) {
                    adapter.setTransactions(transactions)

                    // Calculate unique customers
                    val uniqueCustomers = transactions.mapNotNull { it.userId }.distinct().count()
                    binding.tvTotalCustomers.text = uniqueCustomers.toString()

                    // Count pending transactions
                    val pendingCount = transactions.count { "PENDING".equals(it.status, ignoreCase = true) }
                    binding.tvPendingCount.text = getString(R.string.pending_bookings_count, pendingCount)
                }
            }

            override fun onError(error: String?) {
                showToast(getString(R.string.error_loading_transactions))
            }
        })
    }
}
