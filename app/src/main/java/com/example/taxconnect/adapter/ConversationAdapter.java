package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taxconnect.databinding.ItemConversationBinding;
import com.example.taxconnect.model.ConversationModel;
import com.bumptech.glide.Glide;
import com.example.taxconnect.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    private List<ConversationModel> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;
    private List<String> blockedUsers = new ArrayList<>();
    private String currentUserId;

    public interface OnConversationClickListener {
        void onConversationClick(ConversationModel conversation);
    }

    public ConversationAdapter(OnConversationClickListener listener, String currentUserId) {
        this.listener = listener;
        this.currentUserId = currentUserId;
    }

    public void setBlockedUsers(List<String> blockedUsers) {
        this.blockedUsers = blockedUsers;
        notifyDataSetChanged();
    }

    public void setConversations(List<ConversationModel> list) {
        this.conversations = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ConversationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationModel conversation = conversations.get(position);
        holder.bind(conversation);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    class ConversationViewHolder extends RecyclerView.ViewHolder {

        private final ItemConversationBinding binding;

        public ConversationViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ConversationModel conversation) {
            binding.tvUserName.setText(conversation.getOtherUserName() != null ? conversation.getOtherUserName() : "Unknown User");
            binding.tvLastMessage.setText(conversation.getLastMessage());
            binding.tvTime.setText(formatTime(conversation.getLastMessageTimestamp()));

            // Load Profile Image
            if (conversation.getOtherUserProfileImage() != null && !conversation.getOtherUserProfileImage().isEmpty()) {
                Glide.with(binding.getRoot().getContext())
                        .load(conversation.getOtherUserProfileImage())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivProfile);
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_person);
            }
            
            String status = conversation.getWorkflowState() != null ? conversation.getWorkflowState() : "Discussion";
            
            String otherUserId = "";
            if (conversation.getParticipantIds() != null) {
                for (String id : conversation.getParticipantIds()) {
                    if (!id.equals(currentUserId)) {
                        otherUserId = id;
                        break;
                    }
                }
            }
            
            if (blockedUsers != null && blockedUsers.contains(otherUserId)) {
                status += " (Blocked)";
                binding.tvStatus.setTextColor(android.graphics.Color.RED);
            } else {
                binding.tvStatus.setTextColor(binding.getRoot().getContext().getColor(R.color.primary));
            }

            binding.tvStatus.setText(status);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(conversation);
                }
            });
        }
        
        private String formatTime(long timestamp) {
            Date date = new Date(timestamp);
            Date now = new Date();
            SimpleDateFormat sameDay = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            if (sameDay.format(date).equals(sameDay.format(now))) {
                 SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                 return sdf.format(date);
            } else {
                 SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                 return sdf.format(date);
            }
        }
    }
}
