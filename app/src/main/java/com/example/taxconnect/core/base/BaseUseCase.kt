package com.example.taxconnect.core.base

import com.example.taxconnect.core.common.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class BaseUseCase<in Params, Type> {
    
    abstract suspend fun execute(params: Params): Flow<Resource<Type>>
    
    suspend operator fun invoke(params: Params): Flow<Resource<Type>> = execute(params)
    
    protected fun <T> flowResource(
        block: suspend () -> T
    ): Flow<Resource<T>> = flow {
        try {
            emit(Resource.Loading())
            val result = block()
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "An error occurred"))
        }
    }
}

abstract class BaseUseCaseNoParams<Type> {
    
    abstract suspend fun execute(): Flow<Resource<Type>>
    
    suspend operator fun invoke(): Flow<Resource<Type>> = execute()
    
    protected fun <T> flowResource(
        block: suspend () -> T
    ): Flow<Resource<T>> = flow {
        try {
            emit(Resource.Loading())
            val result = block()
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "An error occurred"))
        }
    }
}