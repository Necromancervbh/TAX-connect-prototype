package com.example.taxconnect.features.wallet

import android.widget.Toast
import com.example.taxconnect.R
import com.example.taxconnect.data.services.PaymentManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import com.example.taxconnect.core.base.BaseActivity
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.features.wallet.TransactionAdapter
import com.example.taxconnect.databinding.ActivityWalletBinding
import com.example.taxconnect.core.common.Resource
import com.example.taxconnect.core.utils.PaymentUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WalletActivity : BaseActivity<ActivityWalletBinding>(), PaymentResultListener {

    override val bindingInflater: (LayoutInflater) -> ActivityWalletBinding = ActivityWalletBinding::inflate
    private val viewModel: WalletViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter
    
    private var currentUserId: String? = null
    private var currentUserEmail: String? = null
    private var currentUserPhone: String? = null
    private var paymentManager: PaymentManager? = null
    private var isAuthenticated = false
    private var pendingDepositAmount = 0.0
    private var bottomSheetDialog: BottomSheetDialog? = null

    override fun initViews() {
        binding.root.visibility = View.INVISIBLE
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        currentUserId = firebaseUser?.uid
        currentUserEmail = firebaseUser?.email ?: ""
        currentUserPhone = firebaseUser?.phoneNumber ?: ""
        paymentManager = PaymentManager()

        if (currentUserId == null) {
            showToast(getString(R.string.auth_error, "User not signed in"))
            finish()
            return
        }

        authenticateUser()
    }

    override fun observeViewModel() {
        // Will be called from onCreate, but we might want to delay if not authenticated
        // However, observing livedata is fine even if UI is hidden
        setupObservers()
    }

    override fun setupListeners() {
        setupToolbar()
        setupActionButtons()
    }

    private fun authenticateUser() {
        isAuthenticated = true
        binding.root.visibility = View.VISIBLE
        try {
            initializeApp()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to initialize wallet")
            android.util.Log.e("WalletActivity", "Failed to initialize wallet", e)
            showToast("Failed to initialize wallet: ${e.message}")
            finish()
        }
    }

    private fun initializeApp() {
        setupRecyclerView()
        // Data loading
        currentUserId?.let { viewModel.loadData(it) }
    }


    private fun setupToolbar() {
        binding.toolbarWallet.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter()
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.balanceState.collect { balance ->
                try {
                    binding.tvWalletBalance.text = "₹ ${PaymentUtils.formatAmount(balance)}"
                } catch (e: Exception) {
                    android.util.Log.e("WalletActivity", "Failed to render balance", e)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.transactionsState.collect { resource ->
                try {
                    when (resource) {
                        is Resource.Loading -> {
                            binding.shimmerViewContainer.root.visibility = View.VISIBLE
                            binding.shimmerViewContainer.root.startShimmer()
                            binding.rvTransactions.visibility = View.GONE
                            binding.layoutEmptyState.root.visibility = View.GONE
                            binding.layoutErrorState.root.visibility = View.GONE
                        }
                        is Resource.Success -> {
                            binding.shimmerViewContainer.root.stopShimmer()
                            binding.shimmerViewContainer.root.visibility = View.GONE
                            
                            val list = resource.data ?: emptyList()
                            adapter.setTransactions(list)
                            if (list.isEmpty()) {
                                binding.rvTransactions.visibility = View.GONE
                                binding.layoutEmptyState.root.visibility = View.VISIBLE
                                
                                binding.layoutEmptyState.tvEmptyTitle.text = "No Transactions"
                                binding.layoutEmptyState.tvEmptyDescription.text = "Your recent transaction history will appear here."
                                binding.layoutEmptyState.ivEmptyIcon.setImageResource(R.drawable.ic_wallet)
                                binding.layoutEmptyState.btnEmptyAction.text = "Add Money"
                                binding.layoutEmptyState.btnEmptyAction.setOnClickListener { binding.btnDeposit.performClick() }
                            } else {
                                binding.layoutEmptyState.root.visibility = View.GONE
                                binding.rvTransactions.visibility = View.VISIBLE
                            }
                        }
                        is Resource.Error -> {
                            binding.shimmerViewContainer.root.stopShimmer()
                            binding.shimmerViewContainer.root.visibility = View.GONE
                            
                            binding.rvTransactions.visibility = View.GONE
                            
                            val errorBinding = binding.layoutErrorState
                            errorBinding.root.visibility = View.VISIBLE
                            errorBinding.tvErrorTitle.text = "Error Loading History"
                            errorBinding.tvErrorDescription.text = "Failed to load transactions. Please check your connection."
                            errorBinding.ivErrorIcon.setImageResource(R.drawable.ic_error) // or ic_cloud_offline
                            errorBinding.btnRetry.setOnClickListener { 
                                errorBinding.root.visibility = View.GONE
                                currentUserId?.let { uid -> viewModel.loadData(uid) } 
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WalletActivity", "Failed to render transactions", e)
                }
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnDeposit.setOnClickListener { showBottomSheet(true) }
        binding.btnWithdraw.setOnClickListener { showBottomSheet(false) }
    }

    private fun showBottomSheet(isDeposit: Boolean) {
        bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.layout_wallet_action_bottom_sheet, null)
        bottomSheetDialog?.setContentView(sheetView)

        val tvTitle = sheetView.findViewById<TextView>(R.id.tvSheetTitle)
        val tilUpiId = sheetView.findViewById<TextInputLayout>(R.id.tilUpiId)
        val etAmount = sheetView.findViewById<TextInputEditText>(R.id.etAmount)
        val etUpiId = sheetView.findViewById<TextInputEditText>(R.id.etUpiId)
        val chipGroup = sheetView.findViewById<ChipGroup>(R.id.chipGroupQuickAmounts)
        val btnProceed = sheetView.findViewById<MaterialButton>(R.id.btnProceed)

        if (isDeposit) {
            tvTitle.text = getString(R.string.add_money_to_wallet)
            tilUpiId.visibility = View.GONE
            btnProceed.text = getString(R.string.add_money)
        } else {
            tvTitle.text = getString(R.string.withdraw_to_bank)
            tilUpiId.visibility = View.VISIBLE
            btnProceed.text = getString(R.string.withdraw_money)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                val text = chip.text.toString()
                val amount = text.replace("[^0-9.]".toRegex(), "").trim()
                etAmount.setText(amount)
                etAmount.setSelection(amount.length)
            }
        }

        btnProceed.setOnClickListener {
            val amountStr = etAmount.text.toString()
            if (amountStr.isEmpty()) {
                etAmount.error = getString(R.string.enter_amount)
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                etAmount.error = getString(R.string.invalid_amount)
                return@setOnClickListener
            }

            if (isDeposit) {
                pendingDepositAmount = amount
                bottomSheetDialog?.dismiss()
                paymentManager?.startPayment(
                    this,
                    amountStr,
                    currentUserEmail ?: "",
                    currentUserPhone ?: "",
                    "Wallet Deposit"
                )
            } else {
                val upiId = etUpiId.text.toString()
                if (upiId.isEmpty()) {
                    etUpiId.error = getString(R.string.enter_upi_id)
                    return@setOnClickListener
                }

                if (!PaymentUtils.isValidUpiId(upiId)) {
                    etUpiId.error = getString(R.string.invalid_upi_id)
                    return@setOnClickListener
                }

                bottomSheetDialog?.dismiss()
                currentUserId?.let { uid ->
                    viewModel.performWithdrawal(uid, amount, upiId,
                        onSuccess = {
                            showToast(getString(R.string.withdrawal_successful), Toast.LENGTH_SHORT)
                        },
                        onError = { error ->
                            showToast(error, Toast.LENGTH_SHORT)
                        }
                    )
                }
            }
        }

        bottomSheetDialog?.show()
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        if (pendingDepositAmount > 0) {
            currentUserId?.let { uid ->
                viewModel.performDeposit(uid, pendingDepositAmount)
                showToast(getString(R.string.deposit_successful), Toast.LENGTH_SHORT)
            }
            pendingDepositAmount = 0.0
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        showToast(getString(R.string.payment_failed, response))
    }
}
