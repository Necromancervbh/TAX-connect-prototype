package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.Expense
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

class ExpenseRepository private constructor() {
    
    private val db = FirebaseFirestore.getInstance()
    private val expensesCollection = db.collection("expenses")
    
    companion object {
        @Volatile
        private var instance: ExpenseRepository? = null
        
        fun getInstance(): ExpenseRepository {
            return instance ?: synchronized(this) {
                instance ?: ExpenseRepository().also { instance = it }
            }
        }
    }
    
    /**
     * Add a new expense
     */
    fun addExpense(expense: Expense, callback: (Boolean, String?) -> Unit) {
        val expenseId = expensesCollection.document().id
        expense.id = expenseId
        expense.createdAt = Timestamp.now()
        
        expensesCollection.document(expenseId)
            .set(expense)
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }
    
    /**
     * Get expenses for a user
     */
    fun getExpenses(userId: String, callback: (List<Expense>?, String?) -> Unit) {
        expensesCollection
            .whereEqualTo("userId", userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val expenses = documents.mapNotNull { it.toObject(Expense::class.java) }
                callback(expenses, null)
            }
            .addOnFailureListener { e ->
                callback(null, e.message)
            }
    }
    
    /**
     * Get expenses for a user within a date range
     */
    fun getExpensesByDateRange(
        userId: String,
        startDate: Long,
        endDate: Long,
        callback: (List<Expense>?, String?) -> Unit
    ) {
        expensesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val expenses = documents.mapNotNull { it.toObject(Expense::class.java) }
                callback(expenses, null)
            }
            .addOnFailureListener { e ->
                callback(null, e.message)
            }
    }
    
    /**
     * Get expenses by category
     */
    fun getExpensesByCategory(
        userId: String,
        category: String,
        callback: (List<Expense>?, String?) -> Unit
    ) {
        expensesCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("category", category)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val expenses = documents.mapNotNull { it.toObject(Expense::class.java) }
                callback(expenses, null)
            }
            .addOnFailureListener { e ->
                callback(null, e.message)
            }
    }
    
    /**
     * Delete an expense
     */
    fun deleteExpense(expenseId: String, callback: (Boolean, String?) -> Unit) {
        expensesCollection.document(expenseId)
            .delete()
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }
    
    /**
     * Calculate total for a list of expenses
     */
    fun calculateTotal(expenses: List<Expense>): Double {
        return expenses.sumOf { it.amount }
    }
    
    /**
     * Get category breakdown
     */
    fun getCategoryBreakdown(expenses: List<Expense>): Map<String, Double> {
        return expenses.groupBy { it.category ?: "Other" }
            .mapValues { (_, expenseList) -> expenseList.sumOf { it.amount } }
    }
    
    /**
     * Get monthly total for current month
     */
    fun getMonthlyTotal(userId: String, callback: (Double, String?) -> Unit) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis
        
        getExpensesByDateRange(userId, startOfMonth, endOfMonth) { expenses, error ->
            if (error != null) {
                callback(0.0, error)
            } else {
                val total = expenses?.sumOf { it.amount } ?: 0.0
                callback(total, null)
            }
        }
    }
    
    /**
     * Get yearly total for current year
     */
    fun getYearlyTotal(userId: String, callback: (Double, String?) -> Unit) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfYear = calendar.timeInMillis
        
        calendar.add(Calendar.YEAR, 1)
        val endOfYear = calendar.timeInMillis
        
        getExpensesByDateRange(userId, startOfYear, endOfYear) { expenses, error ->
            if (error != null) {
                callback(0.0, error)
            } else {
                val total = expenses?.sumOf { it.amount } ?: 0.0
                callback(total, null)
            }
        }
    }
}
