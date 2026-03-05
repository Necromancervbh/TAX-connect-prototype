package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.RatingModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun addRating(rating: RatingModel) {
        val caId = rating.caId!!
        val userId = rating.userId!!

        firestore.runTransaction { transaction ->
            val caRef = firestore.collection("users").document(caId)
            val caSnapshot = transaction.get(caRef)

            val ratingRef = caRef.collection("ratings").document(userId)
            val ratingSnapshot = transaction.get(ratingRef)

            var currentRating = 0.0
            var currentCount: Long = 0

            if (caSnapshot.exists()) {
                currentRating = caSnapshot.getDouble("rating") ?: 0.0
                currentCount = caSnapshot.getLong("ratingCount") ?: 0
            }

            val newRatingVal = rating.rating
            val newAverage: Double
            val newCount: Long

            if (ratingSnapshot.exists()) {
                val oldRatingVal = ratingSnapshot.getDouble("rating") ?: 0.0
                val totalScore = (currentRating * currentCount) - oldRatingVal + newRatingVal
                newCount = currentCount
                newAverage = totalScore / newCount
            } else {
                val totalScore = (currentRating * currentCount) + newRatingVal
                newCount = currentCount + 1
                newAverage = totalScore / newCount
            }

            transaction.set(ratingRef, rating)
            transaction.update(caRef, "rating", newAverage, "ratingCount", newCount)
            null
        }.await()
    }

    suspend fun getRatings(caId: String): List<RatingModel> {
        val snapshot = firestore.collection("users").document(caId)
            .collection("ratings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(RatingModel::class.java) }
    }

    suspend fun submitRating(rating: RatingModel, chatId: String) {
        val caId = rating.caId ?: return

        firestore.runTransaction { transaction ->
            val caRef = firestore.collection("users").document(caId)
            val caSnapshot = transaction.get(caRef)

            val currentRating = caSnapshot.getDouble("rating") ?: 0.0
            val currentCount = caSnapshot.getLong("ratingCount") ?: 0

            val newCount = currentCount + 1
            val newRating = (currentRating * currentCount + rating.rating) / newCount

            val ratingRef = firestore.collection("users").document(caId)
                .collection("ratings").document()
            
            transaction.set(ratingRef, rating)
            transaction.update(caRef, "rating", newRating)
            transaction.update(caRef, "ratingCount", newCount.toInt())

            val convRef = firestore.collection("conversations").document(chatId)
            transaction.update(convRef, "workflowState", ConversationModel.STATE_DISCUSSION)
            null
        }.await()
    }

    suspend fun checkRatingEligibility(userId: String, caId: String): Boolean {
        val snapshot = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .get().await()

        for (doc in snapshot.documents) {
            val conv = doc.toObject(ConversationModel::class.java)
            if (conv != null && conv.participantIds?.contains(caId) == true) {
                val state = conv.workflowState
                if (ConversationModel.STATE_REQUESTED != state &&
                    ConversationModel.STATE_REFUSED != state
                ) {
                    return true
                }
            }
        }
        return false
    }
}
