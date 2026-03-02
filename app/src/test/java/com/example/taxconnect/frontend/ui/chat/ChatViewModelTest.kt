package com.example.taxconnect.features.chat

import com.example.taxconnect.data.repositories.ChatRepository
import com.example.taxconnect.data.repositories.DataRepository
import com.example.taxconnect.data.repositories.WalletRepository
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.core.utils.MainDispatcherRule
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Field

@ExperimentalCoroutinesApi
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: ChatRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        mockkStatic(FirebaseAuth::class)
        val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        val mockFirebaseUser = mockk<FirebaseUser>(relaxed = true)

        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns "test_user_id"

        repository = mockk(relaxed = true)
        walletRepository = mockk(relaxed = true)
        viewModel = ChatViewModel(repository, walletRepository)
    }

    @After
    fun teardown() {
        unmockkStatic(FirebaseAuth::class)
    }

    @Test
    fun `initializeChat observes messages`() = runTest {
        val messages = listOf(MessageModel("uid", "other", "chatId", "Hello", 0L, "TEXT"))
        coEvery { repository.getMessagesFlow("chatId") } returns flowOf(messages)

        val collectedMessages = mutableListOf<List<MessageModel>>()
        backgroundScope.launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
            viewModel.messagesState.collect {
                collectedMessages.add(it)
            }
        }

        viewModel.initializeChat("chatId", "other")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(messages, collectedMessages.last())
    }
}