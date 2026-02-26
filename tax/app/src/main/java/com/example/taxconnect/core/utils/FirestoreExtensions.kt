package com.example.taxconnect.core.utils

import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.tasks.await

/**
 * Shared Firestore utility functions used across repositories.
 */
object FirestoreExtensions {

    /**
     * Updates a single field on a Firestore document with retry logic.
     * @param ref The document reference to update
     * @param field The field name to update
     * @param value The new value
     * @param retries Number of retry attempts remaining
     */
    suspend fun updateFieldWithRetry(
        ref: DocumentReference,
        field: String,
        value: Any?,
        retries: Int = 2
    ) {
        try {
            ref.update(field, value).await()
        } catch (e: Exception) {
            if (retries > 0) {
                updateFieldWithRetry(ref, field, value, retries - 1)
            } else {
                throw e
            }
        }
    }
}
