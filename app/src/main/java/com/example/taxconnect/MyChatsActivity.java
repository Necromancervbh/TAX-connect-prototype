package com.example.taxconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taxconnect.adapter.ConversationAdapter;
import com.example.taxconnect.databinding.ActivityMyChatsBinding;
import com.example.taxconnect.model.ConversationModel;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class MyChatsActivity extends AppCompatActivity implements ConversationAdapter.OnConversationClickListener {

    private ActivityMyChatsBinding binding;
    private DataRepository repository;
    private ConversationAdapter adapter;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyChatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = DataRepository.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        fetchCurrentUser();
        fetchConversations();
    }

    private void fetchCurrentUser() {
        repository.fetchUser(currentUserId, new DataRepository.DataCallback<com.example.taxconnect.model.UserModel>() {
            @Override
            public void onSuccess(com.example.taxconnect.model.UserModel user) {
                if (user != null && user.getBlockedUsers() != null) {
                    adapter.setBlockedUsers(user.getBlockedUsers());
                }
            }

            @Override
            public void onError(String error) {
                // Ignore error for now
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ConversationAdapter(this, currentUserId);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChats.setAdapter(adapter);
    }

    private void fetchConversations() {
        repository.getConversations(currentUserId, new DataRepository.DataCallback<List<ConversationModel>>() {
            @Override
            public void onSuccess(List<ConversationModel> data) {
                if (data.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.rvChats.setVisibility(View.GONE);
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                    binding.rvChats.setVisibility(View.VISIBLE);
                    adapter.setConversations(data);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MyChatsActivity.this, "Error fetching chats: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onConversationClick(ConversationModel conversation) {
        String otherUserId = conversation.getParticipantIds().get(0).equals(currentUserId) ?
                conversation.getParticipantIds().get(1) : conversation.getParticipantIds().get(0);

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("RECEIVER_ID", otherUserId);
        intent.putExtra("RECEIVER_NAME", conversation.getOtherUserName());
        startActivity(intent);
    }
}
