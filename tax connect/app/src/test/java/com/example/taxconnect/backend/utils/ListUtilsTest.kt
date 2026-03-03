package com.example.taxconnect.core.utils

import com.example.taxconnect.data.models.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListUtilsTest {

    private fun createUser(rating: Double): UserModel {
        return UserModel(rating = rating)
    }

    @Test
    fun testProcessCALists_Empty() {
        val input = mutableListOf<UserModel>()
        val result = ListUtils.processCALists(input)
        assertTrue(result.topList.isEmpty())
        assertTrue(result.featuredList.isEmpty())
    }

    @Test
    fun testProcessCALists_Under5() {
        val input = mutableListOf<UserModel>()
        input.add(createUser(4.5))
        input.add(createUser(3.0))
        input.add(createUser(5.0))

        val result = ListUtils.processCALists(input)
        
        assertEquals(3, result.topList.size)
        assertTrue(result.featuredList.isEmpty())
        
        // Verify sort order
        assertEquals(5.0, result.topList[0].rating, 0.01)
        assertEquals(4.5, result.topList[1].rating, 0.01)
        assertEquals(3.0, result.topList[2].rating, 0.01)
    }

    @Test
    fun testProcessCALists_Exact5() {
        val input = mutableListOf<UserModel>()
        for (i in 0 until 5) input.add(createUser(4.0))

        val result = ListUtils.processCALists(input)
        assertEquals(5, result.topList.size)
        assertTrue(result.featuredList.isEmpty())
    }

    @Test
    fun testProcessCALists_MoreThan5() {
        val input = mutableListOf<UserModel>()
        // Add 8 users with ratings 1.0 to 8.0
        for (i in 1..8) {
            input.add(createUser(i.toDouble()))
        }

        val result = ListUtils.processCALists(input)
        
        // Top 5 should be 8.0, 7.0, 6.0, 5.0, 4.0
        assertEquals(5, result.topList.size)
        assertEquals(8.0, result.topList[0].rating, 0.01)
        assertEquals(4.0, result.topList[4].rating, 0.01)
        
        // Featured should be 3.0, 2.0, 1.0
        assertEquals(3, result.featuredList.size)
        assertEquals(3.0, result.featuredList[0].rating, 0.01)
        assertEquals(1.0, result.featuredList[2].rating, 0.01)
    }
    
    @Test
    fun testProcessCALists_MoreThan10() {
        val input = mutableListOf<UserModel>()
        for (i in 0 until 15) input.add(createUser(5.0))

        val result = ListUtils.processCALists(input)
        assertEquals(5, result.topList.size)
        assertEquals(5, result.featuredList.size) // Max 5 featured
    }
}
