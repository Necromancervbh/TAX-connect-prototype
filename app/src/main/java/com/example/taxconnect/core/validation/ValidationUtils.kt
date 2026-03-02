package com.example.taxconnect.core.validation

import android.util.Patterns
import com.example.taxconnect.core.common.Constants
import java.util.regex.Pattern

object ValidationUtils {

    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.Error("Email cannot be empty")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> ValidationResult.Error("Invalid email format")
            else -> ValidationResult.Success
        }
    }

    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password cannot be empty")
            password.length < Constants.Validation.MIN_PASSWORD_LENGTH -> 
                ValidationResult.Error("Password must be at least ${Constants.Validation.MIN_PASSWORD_LENGTH} characters")
            password.length > Constants.Validation.MAX_PASSWORD_LENGTH -> 
                ValidationResult.Error("Password is too long")
            !hasRequiredPasswordStrength(password) -> 
                ValidationResult.Error("Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
            else -> ValidationResult.Success
        }
    }

    fun validateName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Name cannot be empty")
            name.length < Constants.Validation.MIN_NAME_LENGTH -> 
                ValidationResult.Error("Name must be at least ${Constants.Validation.MIN_NAME_LENGTH} characters")
            name.length > Constants.Validation.MAX_NAME_LENGTH -> 
                ValidationResult.Error("Name is too long")
            !name.matches(Regex("^[a-zA-Z\\s]+$")) -> 
                ValidationResult.Error("Name can only contain letters and spaces")
            else -> ValidationResult.Success
        }
    }

    fun validatePhone(phone: String): ValidationResult {
        val cleanPhone = phone.replace("\\s+".toRegex(), "").replace("-", "")
        
        return when {
            cleanPhone.isBlank() -> ValidationResult.Error("Phone number cannot be empty")
            cleanPhone.length < Constants.Validation.MIN_PHONE_LENGTH -> 
                ValidationResult.Error("Phone number is too short")
            cleanPhone.length > Constants.Validation.MAX_PHONE_LENGTH -> 
                ValidationResult.Error("Phone number is too long")
            !Patterns.PHONE.matcher(cleanPhone).matches() -> 
                ValidationResult.Error("Invalid phone number format")
            else -> ValidationResult.Success
        }
    }

    fun validateBio(bio: String): ValidationResult {
        return when {
            bio.length > Constants.Validation.MAX_BIO_LENGTH -> 
                ValidationResult.Error("Bio cannot exceed ${Constants.Validation.MAX_BIO_LENGTH} characters")
            else -> ValidationResult.Success
        }
    }

    fun validateSpecialization(specialization: String): ValidationResult {
        return when {
            specialization.length > Constants.Validation.MAX_SPECIALIZATION_LENGTH -> 
                ValidationResult.Error("Specialization cannot exceed ${Constants.Validation.MAX_SPECIALIZATION_LENGTH} characters")
            else -> ValidationResult.Success
        }
    }

    fun validateAmount(amount: String): ValidationResult {
        return when {
            amount.isBlank() -> ValidationResult.Error("Amount cannot be empty")
            !amount.matches(Regex("^\\d+(\\.\\d{1,2})?$")) -> 
                ValidationResult.Error("Invalid amount format")
            amount.toDoubleOrNull()?.let { it <= 0 } == true -> 
                ValidationResult.Error("Amount must be greater than zero")
            else -> ValidationResult.Success
        }
    }

    fun validateRating(rating: Float): ValidationResult {
        return when {
            rating < 0f || rating > 5f -> ValidationResult.Error("Rating must be between 0 and 5")
            else -> ValidationResult.Success
        }
    }

    fun validateFileSize(sizeInBytes: Long, maxSizeInMB: Int = 10): ValidationResult {
        val maxSizeInBytes = maxSizeInMB * 1024 * 1024
        return when {
            sizeInBytes > maxSizeInBytes -> 
                ValidationResult.Error("File size cannot exceed $maxSizeInMB MB")
            else -> ValidationResult.Success
        }
    }

    fun validateFileExtension(fileName: String, allowedExtensions: List<String>): ValidationResult {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return when {
            extension.isEmpty() -> ValidationResult.Error("File must have an extension")
            !allowedExtensions.contains(extension) -> 
                ValidationResult.Error("File type not allowed. Allowed types: ${allowedExtensions.joinToString(", ")}")
            else -> ValidationResult.Success
        }
    }

    private fun hasRequiredPasswordStrength(password: String): Boolean {
        val hasUppercase = Pattern.compile("[A-Z]").matcher(password).find()
        val hasLowercase = Pattern.compile("[a-z]").matcher(password).find()
        val hasDigit = Pattern.compile("\\d").matcher(password).find()
        val hasSpecialChar = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find()
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    fun isValid(): Boolean = this is Success
    fun getErrorMessage(): String? = (this as? Error)?.message
}