package com.example.taxconnect.features.wallet.usecase

import com.example.taxconnect.data.repositories.WalletRepository
import com.example.taxconnect.core.base.BaseUseCaseNoParams
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWalletBalanceUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) : BaseUseCaseNoParams<Double>() {

    override suspend fun execute(): Flow<com.example.taxconnect.core.common.Resource<Double>> {
        return flowResource {
            val userId = getCurrentUserId() ?: throw Exception("User not logged in")
            walletRepository.getWalletBalance(userId)
        }
    }

    private fun getCurrentUserId(): String? {
        // This would be injected in a real implementation
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}