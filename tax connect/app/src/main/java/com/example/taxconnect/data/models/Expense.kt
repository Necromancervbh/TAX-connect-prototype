package com.example.taxconnect.data.models

import com.google.firebase.Timestamp
import java.io.Serializable

data class Expense(
    var id: String? = null,
    var userId: String? = null,
    var amount: Double = 0.0,
    var category: String? = null, // Travel, Food, Business, Medical, Education, Entertainment, Other
    var description: String? = null,
    var date: Long = System.currentTimeMillis(),
    var timestamp: Long = System.currentTimeMillis(),
    var createdAt: Timestamp? = null
) : Serializable {
    
    companion object {
        const val CATEGORY_TRAVEL = "Travel"
        const val CATEGORY_FOOD = "Food"
        const val CATEGORY_BUSINESS = "Business"
        const val CATEGORY_MEDICAL = "Medical"
        const val CATEGORY_EDUCATION = "Education"
        const val CATEGORY_ENTERTAINMENT = "Entertainment"
        const val CATEGORY_OTHER = "Other"
        
        fun getAllCategories(): List<String> {
            return listOf(
                CATEGORY_TRAVEL,
                CATEGORY_FOOD,
                CATEGORY_BUSINESS,
                CATEGORY_MEDICAL,
                CATEGORY_EDUCATION,
                CATEGORY_ENTERTAINMENT,
                CATEGORY_OTHER
            )
        }
    }
}
