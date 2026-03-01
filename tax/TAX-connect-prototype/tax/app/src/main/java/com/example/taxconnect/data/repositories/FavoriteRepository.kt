package com.example.taxconnect.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun getFavoriteCaIds(userId: String): List<String> {
        val snapshot = firestore.collection("users").document(userId)
            .collection("favorites").get().await()
        return snapshot.documents.map { it.id }
    }

    suspend fun toggleFavorite(userId: String, caId: String): Boolean {
        val ref = firestore.collection("users").document(userId)
            .collection("favorites").document(caId)
        val snapshot = ref.get().await()

        return if (snapshot.exists()) {
            ref.delete().await()
            false // Removed from favorites
        } else {
            ref.set(mapOf(
                "timestamp" to System.currentTimeMillis(),
                "caId" to caId
            )).await()
            true // Added to favorites
        }
    }
}
