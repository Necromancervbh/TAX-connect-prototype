package com.example.taxconnect.core.base

import com.example.taxconnect.core.common.Resource
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

abstract class BaseRepository {
    
    protected suspend fun <T> safeFirebaseCall(
        call: suspend () -> T
    ): Resource<T> {
        return try {
            Resource.Success(call())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Firebase operation failed")
        }
    }

    protected suspend fun <T> getDocument(
        documentRef: DocumentReference,
        clazz: Class<T>
    ): Resource<T> {
        return safeFirebaseCall {
            val snapshot = documentRef.get().await()
            snapshot.toObject(clazz) ?: throw Exception("Document not found")
        }
    }

    protected suspend fun <T> getDocuments(
        query: Query,
        clazz: Class<T>
    ): Resource<List<T>> {
        return safeFirebaseCall {
            val snapshot = query.get().await()
            snapshot.toObjects(clazz)
        }
    }

    protected suspend fun setDocument(
        documentRef: DocumentReference,
        data: Any
    ): Resource<Unit> {
        return safeFirebaseCall {
            documentRef.set(data).await()
        }
    }

    protected suspend fun updateDocument(
        documentRef: DocumentReference,
        updates: Map<String, Any>
    ): Resource<Unit> {
        return safeFirebaseCall {
            documentRef.update(updates).await()
        }
    }

    protected suspend fun deleteDocument(
        documentRef: DocumentReference
    ): Resource<Unit> {
        return safeFirebaseCall {
            documentRef.delete().await()
        }
    }

    protected fun createBatch(firestore: FirebaseFirestore) = firestore.batch()
}