package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.DocumentModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun saveDocument(userId: String, doc: DocumentModel) {
        firestore.collection("users").document(userId)
            .collection("documents").document(doc.id!!)
            .set(doc).await()
    }

    suspend fun getDocuments(userId: String): List<DocumentModel> {
        val snapshot = firestore.collection("users").document(userId)
            .collection("documents")
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(DocumentModel::class.java) }
    }
}
