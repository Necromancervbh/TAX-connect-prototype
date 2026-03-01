package com.example.taxconnect.core.error

import android.content.Context
import com.example.taxconnect.core.common.Constants
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorHandler {

    fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is FirebaseAuthException -> handleFirebaseAuthException(throwable)
            is FirebaseFirestoreException -> handleFirestoreException(throwable)
            is FirebaseException -> handleFirebaseException(throwable)
            is UnknownHostException -> Constants.ErrorMessages.NETWORK_ERROR
            is TimeoutException -> "Request timed out. Please try again."
            is IllegalArgumentException -> Constants.ErrorMessages.INVALID_INPUT
            is SecurityException -> Constants.ErrorMessages.PERMISSION_DENIED
            else -> throwable.message ?: Constants.ErrorMessages.UNKNOWN_ERROR
        }
    }

    fun getUserFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is FirebaseAuthException -> "Authentication failed. Please try again."
            is FirebaseFirestoreException -> "Data operation failed. Please try again."
            is UnknownHostException -> "No internet connection. Please check your network."
            is TimeoutException -> "Request took too long. Please try again."
            is IllegalArgumentException -> "Invalid input provided."
            is SecurityException -> "You don't have permission to perform this action."
            else -> "Something went wrong. Please try again."
        }
    }

    private fun handleFirebaseAuthException(exception: FirebaseAuthException): String {
        return when (exception.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Invalid email address."
            "ERROR_WRONG_PASSWORD" -> "Incorrect password."
            "ERROR_USER_NOT_FOUND" -> "User not found."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
            "ERROR_OPERATION_NOT_ALLOWED" -> "This operation is not allowed."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already registered."
            "ERROR_WEAK_PASSWORD" -> "Password is too weak."
            else -> exception.message ?: "Authentication failed."
        }
    }

    private fun handleFirestoreException(exception: FirebaseFirestoreException): String {
        return when (exception.code) {
            FirebaseFirestoreException.Code.CANCELLED -> "Operation was cancelled."
            FirebaseFirestoreException.Code.UNKNOWN -> "Unknown error occurred."
            FirebaseFirestoreException.Code.INVALID_ARGUMENT -> "Invalid input provided."
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Request timed out."
            FirebaseFirestoreException.Code.NOT_FOUND -> "Requested data not found."
            FirebaseFirestoreException.Code.ALREADY_EXISTS -> "Data already exists."
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permission denied."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "Resource limit exceeded."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Operation failed preconditions."
            FirebaseFirestoreException.Code.ABORTED -> "Operation was aborted."
            FirebaseFirestoreException.Code.OUT_OF_RANGE -> "Input out of range."
            FirebaseFirestoreException.Code.UNIMPLEMENTED -> "Operation not implemented."
            FirebaseFirestoreException.Code.INTERNAL -> "Internal error occurred."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Service unavailable."
            FirebaseFirestoreException.Code.DATA_LOSS -> "Data loss occurred."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Authentication required."
            else -> exception.message ?: "Firestore operation failed."
        }
    }

    private fun handleFirebaseException(exception: FirebaseException): String {
        return exception.message ?: "Firebase operation failed."
    }

    fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is UnknownHostException || 
               throwable.message?.contains("network", ignoreCase = true) == true ||
               (throwable is FirebaseFirestoreException && 
                throwable.code == FirebaseFirestoreException.Code.UNAVAILABLE)
    }

    fun isAuthenticationError(throwable: Throwable): Boolean {
        return throwable is FirebaseAuthException ||
               (throwable is FirebaseFirestoreException && 
                throwable.code == FirebaseFirestoreException.Code.UNAUTHENTICATED)
    }

    fun isPermissionError(throwable: Throwable): Boolean {
        return throwable is SecurityException ||
               (throwable is FirebaseFirestoreException && 
                throwable.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
}