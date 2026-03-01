package com.example.taxconnect.features.auth.usecase

import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.core.base.BaseUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val userRepository: UserRepository
) : BaseUseCase<LoginUseCase.Params, UserModel>() {

    data class Params(
        val email: String,
        val password: String
    )

    override suspend fun execute(params: Params): Flow<com.example.taxconnect.core.common.Resource<UserModel>> {
        return flowResource {
            userRepository.loginUser(params.email, params.password)
        }
    }
}