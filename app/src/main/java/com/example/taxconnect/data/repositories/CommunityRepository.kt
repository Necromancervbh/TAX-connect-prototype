package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.CommentModel
import com.example.taxconnect.data.models.PostModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    data class PageResult<T>(val items: List<T>, val lastSnapshot: DocumentSnapshot?)

    suspend fun createPost(post: PostModel) {
        firestore.collection("posts").document(post.id!!).set(post).await()
    }

    suspend fun getPosts(): List<PostModel> {
        return getPostsPage(25, null).items
    }

    suspend fun getPostsPage(limit: Int, lastSnapshot: DocumentSnapshot?): PageResult<PostModel> {
        var query: Query = firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snapshot = query.get().await()
        val list = snapshot.documents.mapNotNull { it.toObject(PostModel::class.java) }
        val lastDoc = snapshot.documents.lastOrNull()
        return PageResult(list, lastDoc)
    }

    suspend fun toggleLike(postId: String, userId: String): Boolean {
        val postRef = firestore.collection("posts").document(postId)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            if (!snapshot.exists()) throw Exception("Post not found")

            val post = snapshot.toObject(PostModel::class.java) ?: throw Exception("Post not found")
            val likedBy = post.likedBy
            val isLiked: Boolean

            if (likedBy.contains(userId)) {
                likedBy.remove(userId)
                isLiked = false
            } else {
                likedBy.add(userId)
                isLiked = true
            }

            transaction.update(postRef, "likedBy", likedBy, "likeCount", likedBy.size)
            isLiked
        }.await()
    }

    suspend fun getComments(postId: String): List<CommentModel> {
        return getCommentsPage(postId, 50, null).items
    }

    suspend fun getCommentsPage(
        postId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<CommentModel> {
        var query: Query = firestore.collection("posts").document(postId)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snapshot = query.get().await()
        val list = snapshot.documents
            .mapNotNull { it.toObject(CommentModel::class.java) }
            .reversed()
        val lastDoc = snapshot.documents.lastOrNull()
        return PageResult(list, lastDoc)
    }

    suspend fun addComment(postId: String, comment: CommentModel) {
        firestore.collection("posts").document(postId)
            .collection("comments").document(comment.id!!).set(comment).await()
        // Update comment count
        firestore.collection("posts").document(postId)
            .update("commentCount", FieldValue.increment(1))
    }
}
