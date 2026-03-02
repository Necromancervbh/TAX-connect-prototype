package com.example.taxconnect.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.core.common.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    protected fun <T> executeUseCase(
        useCase: suspend () -> T,
        onLoading: (() -> Unit)? = null,
        onSuccess: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                onLoading?.invoke()
                val result = useCase()
                onSuccess(result)
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    protected fun <T> MutableStateFlow<Resource<T>>.setLoading() {
        value = Resource.Loading()
    }

    protected fun <T> MutableStateFlow<Resource<T>>.setSuccess(data: T) {
        value = Resource.Success(data)
    }

    protected fun <T> MutableStateFlow<Resource<T>>.setError(message: String) {
        value = Resource.Error(message)
    }

    protected fun <T> MutableStateFlow<Resource<T>>.setError(throwable: Throwable) {
        value = Resource.Error(throwable.message ?: "Unknown error occurred")
    }
}