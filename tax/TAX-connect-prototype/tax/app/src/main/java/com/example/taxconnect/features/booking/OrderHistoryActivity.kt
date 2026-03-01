package com.example.taxconnect.features.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.features.wallet.TransactionAdapter
import com.example.taxconnect.databinding.ActivityOrderHistoryBinding
import com.example.taxconnect.data.models.TransactionModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.DataRepository
import com.google.firebase.auth.FirebaseAuth
import com.example.taxconnect.core.base.BaseActivity

class OrderHistoryActivity : BaseActivity<ActivityOrderHistoryBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityOrderHistoryBinding = ActivityOrderHistoryBinding::inflate
    private lateinit var adapter: TransactionAdapter

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter()
        binding.recyclerView.adapter = adapter

        loadTransactions()
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    override fun observeViewModel() {
        // No ViewModel yet
    }

    private fun loadTransactions() {
        val uid = FirebaseAuth.getInstance().uid ?: return

        binding.progressBar.visibility = View.VISIBLE

        // First we need to know if the user is a CA or Customer to call the right method
        DataRepository.getInstance().fetchUser(uid, object : DataRepository.DataCallback<UserModel> {
            override fun onSuccess(data: UserModel?) {
                if (data == null) {
                    binding.progressBar.visibility = View.GONE
                    return
                }

                if ("CA" == data.role) {
                    fetchCaTransactions(uid)
                } else {
                    fetchCustomerTransactions(uid)
                }
            }

            override fun onError(error: String?) {
                binding.progressBar.visibility = View.GONE
                showToast("Error loading profile: $error")
            }
        })
    }

    private fun fetchCustomerTransactions(uid: String) {
        DataRepository.getInstance().getTransactions(uid, object : DataRepository.DataCallback<List<TransactionModel>> {
            override fun onSuccess(data: List<TransactionModel>?) {
                binding.progressBar.visibility = View.GONE
                if (data == null || data.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    adapter.setTransactions(data)
                }
            }

            override fun onError(error: String?) {
                binding.progressBar.visibility = View.GONE
                showToast(getString(R.string.error_with_message, error))
            }
        })
    }

    private fun fetchCaTransactions(uid: String) {
        DataRepository.getInstance().getCaTransactions(uid, object : DataRepository.DataCallback<List<TransactionModel>> {
            override fun onSuccess(data: List<TransactionModel>?) {
                binding.progressBar.visibility = View.GONE
                if (data == null || data.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    adapter.setTransactions(data)
                }
            }

            override fun onError(error: String?) {
                binding.progressBar.visibility = View.GONE
                showToast(getString(R.string.error_with_message, error))
            }
        })
    }
}
