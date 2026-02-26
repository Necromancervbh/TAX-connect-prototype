package com.example.taxconnect.features.home

import com.example.taxconnect.data.repositories.HomeRepository
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.core.utils.MainDispatcherRule
import com.example.taxconnect.core.common.Resource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Field

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: HomeRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        repository = mockk()
        viewModel = HomeViewModel(repository)
    }

    @Test
    fun `fetchCAs success updates state to Success`() = runTest {
        val mockList = listOf(UserModel("1", "test@email.com", "CA 1", "CA"))
        coEvery { repository.getCAList() } returns mockList

        viewModel.fetchCAs()

        val state = viewModel.caListState.value
        assertTrue(state is Resource.Success)
        assertEquals(mockList, (state as Resource.Success).data)
    }

    @Test
    fun `fetchCAs error updates state to Error`() = runTest {
        coEvery { repository.getCAList() } throws Exception("Network Error")

        viewModel.fetchCAs()

        val state = viewModel.caListState.value
        assertTrue(state is Resource.Error)
        assertEquals("Network Error", (state as Resource.Error).message)
    }
}