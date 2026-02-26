package com.example.taxconnect.features.dashboard

import com.example.taxconnect.data.repositories.DashboardRepository
import com.example.taxconnect.core.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Field

@ExperimentalCoroutinesApi
class CADashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: DashboardRepository
    private lateinit var viewModel: CADashboardViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        viewModel = CADashboardViewModel(repository)
    }

    @Test
    fun `loadDashboardData fetches revenue`() = runTest {
        coEvery { repository.getRevenueStats("uid") } returns 5000.0

        viewModel.loadDashboardData("uid")

        // Wait for coroutines? runTest uses UnconfinedTestDispatcher so it should execute immediately if using Main
        assertEquals(5000.0, viewModel.revenueState.value, 0.0)
    }
}