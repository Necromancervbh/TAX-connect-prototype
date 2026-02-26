package com.example.taxconnect.features.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxconnect.data.repositories.ServiceRepository
import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.data.models.ServiceModel
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.common.Resource
import com.example.taxconnect.data.services.CloudinaryHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    private val _userState = MutableStateFlow<Resource<UserModel>>(Resource.Loading())
    val userState: StateFlow<Resource<UserModel>> = _userState.asStateFlow()

    private val _servicesState = MutableStateFlow<Resource<List<ServiceModel>>>(Resource.Loading())
    val servicesState: StateFlow<Resource<List<ServiceModel>>> = _servicesState.asStateFlow()

    private val _updateState = MutableStateFlow<Resource<Unit>>(Resource.Success(Unit))
    val updateState: StateFlow<Resource<Unit>> = _updateState.asStateFlow()

    private val _uploadState = MutableStateFlow<Resource<String>>(Resource.Loading(null))
    val uploadState: StateFlow<Resource<String>> = _uploadState.asStateFlow()

    fun fetchUser(uid: String) {
        viewModelScope.launch {
            _userState.value = Resource.Loading()
            try {
                val user = userRepository.fetchUser(uid)
                _userState.value = Resource.Success(user)
                if ("CA" == user.role) {
                    fetchServices(uid)
                }
            } catch (e: Exception) {
                _userState.value = Resource.Error(e.message ?: "Failed to load profile")
            }
        }
    }

    private fun fetchServices(uid: String) {
        viewModelScope.launch {
            try {
                val services = serviceRepository.getServices(uid)
                _servicesState.value = Resource.Success(services)
            } catch (e: Exception) {
                _servicesState.value = Resource.Error(e.message ?: "Failed to load services")
            }
        }
    }

    fun updateUser(user: UserModel) {
        viewModelScope.launch {
            _updateState.value = Resource.Loading()
            try {
                // Determine if we should use updateUser (set) or updateUserProfile (update fields)
                // For simplicity, we use the one that takes UserModel and sets it, or updates it.
                // UserRepository has updateUser(UserModel) which sets it.
                userRepository.updateUser(user) 
                _updateState.value = Resource.Success(Unit)
                _userState.value = Resource.Success(user)
            } catch (e: Exception) {
                _updateState.value = Resource.Error(e.message ?: "Update failed")
            }
        }
    }

    fun updateProfileWithMedia(context: Context, user: UserModel, imageUri: Uri?, videoUri: Uri?) {
        viewModelScope.launch {
            _updateState.value = Resource.Loading()
            try {
                if (imageUri != null) {
                    val imageUrl = uploadMediaSuspend(context, imageUri)
                    user.profileImageUrl = imageUrl
                }
                
                if (videoUri != null) {
                    val videoUrl = uploadMediaSuspend(context, videoUri)
                    user.introVideoUrl = videoUrl
                }
                
                userRepository.updateUser(user)
                _updateState.value = Resource.Success(Unit)
                _userState.value = Resource.Success(user)
            } catch (e: Exception) {
                _updateState.value = Resource.Error(e.message ?: "Update failed")
            }
        }
    }

    fun saveService(service: ServiceModel) {
        viewModelScope.launch {
            try {
                serviceRepository.saveService(service)
                // Reflect change locally without re-fetching if possible, or just re-fetch
                val currentServices = (_servicesState.value as? Resource.Success)?.data?.toMutableList() ?: mutableListOf()
                val index = currentServices.indexOfFirst { it.id == service.id }
                if (index != -1) {
                    currentServices[index] = service
                } else {
                    currentServices.add(service)
                }
                _servicesState.value = Resource.Success(currentServices)
            } catch (e: Exception) {
                // Handled in UI via error state or events if needed
            }
        }
    }

    fun deleteService(serviceId: String) {
        viewModelScope.launch {
            try {
                serviceRepository.deleteService(serviceId)
                val currentServices = (_servicesState.value as? Resource.Success)?.data?.toMutableList() ?: return@launch
                currentServices.removeAll { it.id == serviceId }
                _servicesState.value = Resource.Success(currentServices)
            } catch (e: Exception) {
                // Handled in UI
            }
        }
    }

    fun uploadMedia(context: Context, uri: Uri, type: String) {
        viewModelScope.launch {
            _uploadState.value = Resource.Loading()
            try {
                val url = uploadMediaSuspend(context, uri)
                _uploadState.value = Resource.Success(url)
            } catch (e: Exception) {
                _uploadState.value = Resource.Error(e.message ?: "Upload failed")
            }
        }
    }
    
    fun clearUploadState() {
        _uploadState.value = Resource.Loading(null)
    }

    private suspend fun uploadMediaSuspend(context: Context, uri: Uri): String = suspendCancellableCoroutine { continuation ->
        CloudinaryHelper.uploadMedia(context, uri, object : CloudinaryHelper.ImageUploadCallback {
            override fun onSuccess(url: String?) {
                if (continuation.isActive) {
                    if (url != null) continuation.resume(url)
                    else continuation.resumeWithException(Exception("Upload failed"))
                }
            }

            override fun onError(error: String?) {
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception(error ?: "Upload failed"))
                }
            }
        })
    }
}