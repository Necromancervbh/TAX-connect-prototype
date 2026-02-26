package com.example.taxconnect.features.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taxconnect.R
import com.example.taxconnect.databinding.ActivityMyChatsBinding
import com.example.taxconnect.features.chat.ConversationAdapter
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.core.common.Resource
import com.example.taxconnect.core.base.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.ArrayList

@AndroidEntryPoint
class MyChatsActivity : BaseActivity<ActivityMyChatsBinding>(), ConversationAdapter.OnConversationClickListener {

    override val bindingInflater: (LayoutInflater) -> ActivityMyChatsBinding = ActivityMyChatsBinding::inflate
    private val viewModel: MyChatsViewModel by viewModels()
    private lateinit var adapter: ConversationAdapter
    private var currentUserId: String? = null
    private var allConversations: List<ConversationModel> = ArrayList()
    private var currentSearchQuery = ""

    override fun initViews() {
        currentUserId = FirebaseAuth.getInstance().uid
        if (currentUserId == null) {
            finish()
            return
        }

        setupRecyclerView()
        setupSearch()

        currentUserId?.let { uid ->
            viewModel.fetchCurrentUser(uid)
            viewModel.fetchConversations(uid)
        }
    }

    override fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.conversationsState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.shimmerViewContainer.root.visibility = View.VISIBLE
                        binding.shimmerViewContainer.root.startShimmer()
                        binding.rvChats.visibility = View.GONE
                        binding.layoutEmptyState.root.visibility = View.GONE
                    }
                    is Resource.Success -> {
                        binding.shimmerViewContainer.root.stopShimmer()
                        binding.shimmerViewContainer.root.visibility = View.GONE
                        allConversations = resource.data ?: emptyList()
                        applySearch(currentSearchQuery)
                    }
                    is Resource.Error -> {
                        binding.shimmerViewContainer.root.stopShimmer()
                        binding.shimmerViewContainer.root.visibility = View.GONE
                        
                        if (allConversations.isEmpty()) {
                            binding.rvChats.visibility = View.GONE
                            val errorBinding = binding.layoutErrorState
                            errorBinding.root.visibility = View.VISIBLE
                            errorBinding.tvErrorTitle.text = "Error Loading Chats"
                            errorBinding.tvErrorDescription.text = resource.message
                            errorBinding.btnRetry.setOnClickListener {
                                errorBinding.root.visibility = View.GONE
                                currentUserId?.let { viewModel.fetchConversations(it) }
                            }
                        } else {
                            showToast(resource.message ?: "Error loading chats")
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.blockedUsersState.collect { blockedUsers ->
                adapter.setBlockedUsers(blockedUsers)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(this, currentUserId!!)
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                applySearch(currentSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applySearch(query: String) {
        val normalized = query.trim().lowercase()
        val filtered = ArrayList<ConversationModel>()
        
        for (conversation in allConversations) {
            val name = conversation.otherUserName ?: ""
            val message = conversation.lastMessage ?: ""
            val status = conversation.workflowState ?: ""
            
            val matches = normalized.isEmpty() ||
                    name.lowercase().contains(normalized) ||
                    message.lowercase().contains(normalized) ||
                    status.lowercase().contains(normalized)
            
            if (matches) {
                filtered.add(conversation)
            }
        }

        if (filtered.isEmpty()) {
            binding.rvChats.visibility = View.GONE
            binding.layoutEmptyState.root.visibility = View.VISIBLE
            
            if (query.isNotEmpty()) {
                binding.layoutEmptyState.tvEmptyTitle.text = "No results found"
                binding.layoutEmptyState.tvEmptyDescription.text = "We couldn't find any conversations matching \"$query\""
                binding.layoutEmptyState.ivEmptyIcon.setImageResource(R.drawable.ic_search)
                binding.layoutEmptyState.btnEmptyAction.text = "Clear Search"
                binding.layoutEmptyState.btnEmptyAction.setOnClickListener { binding.etSearch.setText("") }
            } else {
                binding.layoutEmptyState.tvEmptyTitle.text = "No Messages"
                binding.layoutEmptyState.tvEmptyDescription.text = "Start chatting with your CA to see conversations here."
                binding.layoutEmptyState.ivEmptyIcon.setImageResource(R.drawable.ic_chat_bubble)
                binding.layoutEmptyState.btnEmptyAction.text = "Find a CA"
                binding.layoutEmptyState.btnEmptyAction.setOnClickListener { finish() }
            }
        } else {
            binding.rvChats.visibility = View.VISIBLE
            binding.layoutEmptyState.root.visibility = View.GONE
        }
        adapter.setConversations(filtered)
    }

    override fun onConversationClick(conversation: ConversationModel) {
        val participants = conversation.participantIds ?: return
        if (participants.isEmpty()) return
        
        val otherUserId = if (participants[0] == currentUserId) {
            if (participants.size > 1) participants[1] else null
        } else {
            participants[0]
        }
        
        if (otherUserId != null) {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("otherUserId", otherUserId)
            intent.putExtra("otherUserName", conversation.otherUserName ?: "Unknown")
            intent.putExtra("chatId", conversation.conversationId)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Cannot open chat: ${e.message}")
            }
        } else {
             showToast("Cannot open chat: Invalid user data")
        }
    }
}

// Extension to match ViewModel method name which I might have named differently
private fun MyChatsViewModel.fetchCurrentUser(uid: String) = this.fetchUser(uid)
