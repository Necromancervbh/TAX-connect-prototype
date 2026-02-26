package com.example.taxconnect.features.wallet

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.example.taxconnect.R
import com.example.taxconnect.data.repositories.WalletRepository
import com.example.taxconnect.data.models.TransactionModel
import com.example.taxconnect.core.common.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    application: Application,
    private val repository: WalletRepository
) : AndroidViewModel(application) {

    private val _balanceState = MutableStateFlow<Double>(0.0)
    val balanceState: StateFlow<Double> = _balanceState.asStateFlow()

    private val _transactionsState = MutableStateFlow<Resource<List<TransactionModel>>>(Resource.Loading())
    val transactionsState: StateFlow<Resource<List<TransactionModel>>> = _transactionsState.asStateFlow()

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(resId: Int, vararg formatArgs: Any): String = getApplication<Application>().getString(resId, *formatArgs)

    fun loadData(uid: String) {
        fetchBalance(uid)
        fetchTransactions(uid)
    }

    fun fetchBalance(uid: String) {
        viewModelScope.launch {
            try {
                val bal = repository.getWalletBalance(uid)
                _balanceState.value = bal
            } catch (e: Exception) {
                _balanceState.value = 0.0
            }
        }
    }

    fun fetchTransactions(uid: String) {
        viewModelScope.launch {
            _transactionsState.value = Resource.Loading()
            try {
                val list = repository.getTransactions(uid)
                _transactionsState.value = Resource.Success(list)
            } catch (e: Exception) {
                _transactionsState.value = Resource.Error(e.message ?: getString(R.string.failed_to_load_transactions))
            }
        }
    }

    fun performDeposit(uid: String, amount: Double) {
        viewModelScope.launch {
            try {
                // Fetch user info for denormalization
                val user = repository.getUser(uid)
                
                val txId = UUID.randomUUID().toString()
                val transaction = TransactionModel(
                    txId,
                    uid,
                    "WALLET",
                    getString(R.string.wallet_top_up),
                    getString(R.string.deposit_razorpay),
                    amount,
                    "SUCCESS"
                )
                transaction.type = "WALLET_DEPOSIT"
                transaction.userName = user?.name
                transaction.userEmail = user?.email
                
                // Update balance first, then create record
                val newBal = repository.updateWalletBalance(uid, amount)
                repository.createTransaction(transaction)
                
                _balanceState.value = newBal
                fetchTransactions(uid)
            } catch (e: Exception) {
                _transactionsState.value = Resource.Error(e.message ?: getString(R.string.transaction_failed))
            }
        }
    }

    fun performWithdrawal(uid: String, amount: Double, upiId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (amount > _balanceState.value) {
                    onError(getString(R.string.insufficient_balance))
                    return@launch
                }
                
                val transaction = TransactionModel(
                    UUID.randomUUID().toString(),
                    uid,
                    "BANK",
                    getString(R.string.bank_transfer),
                    getString(R.string.withdrawal_to_upi, upiId),
                    -amount,
                    "SUCCESS"
                )
                transaction.type = "WALLET_WITHDRAWAL"
                repository.createTransaction(transaction)
                
                val newBal = repository.updateWalletBalance(uid, -amount)
                _balanceState.value = newBal
                fetchTransactions(uid)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: getString(R.string.transaction_failed))
            }
        }
    }
}
