package com.example.taxconnect.data.repositories

import com.example.taxconnect.core.utils.FirestoreExtensions
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.UserModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    data class PageResult<T>(val items: List<T>, val lastSnapshot: DocumentSnapshot?)

    suspend fun saveBooking(booking: BookingModel) {
        val id = booking.id ?: firestore.collection("bookings").document().id
        booking.id = id
        firestore.collection("bookings").document(id).set(booking).await()
    }

    suspend fun getBookingsForUser(userId: String): List<BookingModel> {
        return getBookingsForUserPage(userId, 50, null).items
    }

    suspend fun getBookingsForCA(caId: String): List<BookingModel> {
        return getBookingsForCaPage(caId, 50, null).items
    }

    suspend fun getBookingsForUserPage(
        userId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<BookingModel> {
        try {
            var query: Query = firestore.collection("bookings")
                .whereEqualTo("userId", userId)
                .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
            val lastDoc = snapshot.documents.lastOrNull()
            return PageResult(list, lastDoc)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("index") || msg.contains("FAILED_PRECONDITION")) {
                // Fallback if composite index not ready
                val fallback = firestore.collection("bookings")
                    .whereEqualTo("userId", userId)
                    .limit(limit.toLong())
                    .get().await()
                val list = fallback.documents
                    .mapNotNull { it.toObject(BookingModel::class.java) }
                    .sortedByDescending { it.appointmentTimestamp }
                return PageResult(list, null)
            }
            throw e
        }
    }

    suspend fun getBookingsForCaPage(
        caId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<BookingModel> {
        try {
            var query: Query = firestore.collection("bookings")
                .whereEqualTo("caId", caId)
                .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
            val enriched = populateBookingUserDetails(list)
            val lastDoc = snapshot.documents.lastOrNull()
            return PageResult(enriched, lastDoc)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("index") || msg.contains("FAILED_PRECONDITION")) {
                val fallback = firestore.collection("bookings")
                    .whereEqualTo("caId", caId)
                    .limit(limit.toLong())
                    .get().await()
                val list = fallback.documents
                    .mapNotNull { it.toObject(BookingModel::class.java) }
                    .sortedByDescending { it.appointmentTimestamp }
                val enriched = populateBookingUserDetails(list)
                return PageResult(enriched, null)
            }
            throw e
        }
    }

    private suspend fun populateBookingUserDetails(bookings: List<BookingModel>): List<BookingModel> {
        val tasks = bookings.mapNotNull { booking ->
            booking.userId?.let { firestore.collection("users").document(it).get() }
        }
        if (tasks.isEmpty()) return bookings

        try {
            val results = Tasks.whenAllSuccess<DocumentSnapshot>(tasks).await()
            for (userDoc in results) {
                if (userDoc.exists()) {
                    val user = userDoc.toObject(UserModel::class.java)
                    if (user != null) {
                        val uid = user.uid ?: userDoc.id
                        bookings.filter { it.userId == uid }.forEach { it.userName = user.name }
                    }
                }
            }
        } catch (_: Exception) {
            // Return bookings without enrichment on failure
        }
        return bookings
    }

    suspend fun updateBookingStatus(bookingId: String, status: String) {
        val ref = firestore.collection("bookings").document(bookingId)
        FirestoreExtensions.updateFieldWithRetry(ref, "status", status)
    }

    suspend fun incrementClientCount(caId: String, userId: String) {
        val clientRef = firestore.collection("users").document(caId)
            .collection("clients").document(userId)
        val snapshot = clientRef.get().await()
        if (!snapshot.exists()) {
            val batch = firestore.batch()
            batch.set(clientRef, mapOf("timestamp" to System.currentTimeMillis()))
            val caRef = firestore.collection("users").document(caId)
            batch.update(caRef, "clientCount", FieldValue.increment(1))
            batch.commit().await()
        }
    }

    suspend fun isReturningClient(caId: String, userId: String): Boolean {
        val snapshot = firestore.collection("users").document(caId)
            .collection("clients").document(userId).get().await()
        return snapshot.exists()
    }
}
