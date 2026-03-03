package com.example.taxconnect.data.repositories

import com.example.taxconnect.core.utils.FirestoreExtensions
import com.example.taxconnect.data.models.MilestoneModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun getMilestones(bookingId: String): List<MilestoneModel> {
        val snapshot = firestore.collection("bookings").document(bookingId)
            .collection("milestones")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(MilestoneModel::class.java) }
    }

    suspend fun updateMilestoneStatus(bookingId: String, milestoneId: String, status: String) {
        val ref = firestore.collection("bookings").document(bookingId)
            .collection("milestones").document(milestoneId)
        FirestoreExtensions.updateFieldWithRetry(ref, "status", status)
    }

    suspend fun addMilestone(bookingId: String, milestone: MilestoneModel) {
        firestore.collection("bookings").document(bookingId)
            .collection("milestones").document(milestone.id!!)
            .set(milestone).await()
    }
}
