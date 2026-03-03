package com.example.taxconnect.features.profile

import com.example.taxconnect.data.repositories.ProfileRepository
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
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: ProfileRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        viewModel = ProfileViewModel(repository)
    }

    @Test
    fun `fetchUser success updates state`() = runTest {
        val user = UserModel("uid", "email", "name", "role")
        coEvery { repository.fetchUser("uid") } returns user

        viewModel.fetchUser("uid")

        val state = viewModel.userState.value
        assertTrue(state is Resource.Success)
        assertEquals(user, (state as Resource.Success).data)
    }
}