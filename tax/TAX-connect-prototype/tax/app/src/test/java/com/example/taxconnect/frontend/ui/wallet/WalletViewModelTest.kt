package com.example.taxconnect.features.wallet

import android.app.Application
import com.example.taxconnect.data.repositories.WalletRepository
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
class WalletViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var repository: WalletRepository
    private lateinit var viewModel: WalletViewModel

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        viewModel = WalletViewModel(application, repository)
    }

    @Test
    fun `fetchBalance updates state`() = runTest {
        coEvery { repository.getWalletBalance("uid") } returns 100.0

        viewModel.fetchBalance("uid")

        assertEquals(100.0, viewModel.balanceState.value, 0.0)
    }
}