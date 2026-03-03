package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.models.ServiceModel
import com.example.taxconnect.data.services.CloudinaryHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val userRepository: UserRepository,
    private val serviceRepository: ServiceRepository
) {

    suspend fun fetchUser(uid: String): UserModel {
        return userRepository.fetchUser(uid)
    }

    suspend fun updateUserProfile(uid: String, updates: Map<String, Any>) {
        userRepository.updateUserProfile(uid, updates)
    }

    suspend fun getServices(caId: String): List<ServiceModel> {
        return serviceRepository.getServices(caId)
    }

    suspend fun uploadImage(filePath: String): String = suspendCancellableCoroutine { continuation ->
        CloudinaryHelper.uploadImage(filePath, object : CloudinaryHelper.ImageUploadCallback {
            override fun onSuccess(url: String?) {
                if (continuation.isActive) {
                    if (url != null) continuation.resume(url)
                    else continuation.resumeWithException(Exception("Upload failed"))
                }
            }
            override fun onError(error: String?) {
                if (continuation.isActive) continuation.resumeWithException(Exception(error))
            }
        })
    }
}