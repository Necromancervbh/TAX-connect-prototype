package com.example.taxconnect.core.utils

import com.example.taxconnect.data.models.UserModel

object ListUtils {
    data class CAPartitionResult(
        val topList: List<UserModel>,
        val featuredList: List<UserModel>
    )

    fun processCALists(input: List<UserModel>): CAPartitionResult {
        if (input.isEmpty()) {
            return CAPartitionResult(emptyList(), emptyList())
        }

        // Sort by rating descending
        val sortedList = input.sortedByDescending { it.rating ?: 0.0 }

        val topList = sortedList.take(5)
        val remaining = if (sortedList.size > 5) sortedList.drop(5) else emptyList()
        val featuredList = remaining.take(5)

        return CAPartitionResult(topList, featuredList)
    }
}
