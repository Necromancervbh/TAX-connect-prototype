package com.example.taxconnect.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.taxconnect.R;
import com.example.taxconnect.model.MessageModel;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_PROPOSAL = 3;
    private static final int VIEW_TYPE_CALL_REQUEST = 4;

    private List<MessageModel> messages = new ArrayList<>();
    private final String currentUserId;
    private final OnProposalActionListener proposalListener;
    private OnCallActionListener callListener;

    public interface OnProposalActionListener {
        void onAccept(MessageModel proposal);
        void onReject(MessageModel proposal);
    }
    
    public interface OnCallActionListener {
        void onJoinCall(MessageModel message);
        void onRequestAccept(MessageModel message);
        void onRequestReject(MessageModel message);
    }

    public MessageAdapter(OnProposalActionListener proposalListener) {
        this.currentUserId = FirebaseAuth.getInstance().getUid();
        this.proposalListener = proposalListener;
    }
    
    public void setOnCallActionListener(OnCallActionListener listener) {
        this.callListener = listener;
    }
    
    // Default constructor for backward compatibility or when no listener needed
    public MessageAdapter() {
        this(null);
    }

    public void setMessages(List<MessageModel> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }
    
    public void addMessage(MessageModel message) {
        this.messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        MessageModel message = messages.get(position);
        if ("PROPOSAL".equals(message.getType())) {
            return VIEW_TYPE_PROPOSAL;
        } else if ("CALL_REQUEST".equals(message.getType())) {
            return VIEW_TYPE_CALL_REQUEST;
        } else if (message.getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_PROPOSAL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_proposal, parent, false);
            return new ProposalViewHolder(view);
        } else if (viewType == VIEW_TYPE_CALL_REQUEST) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_call_request, parent, false);
            return new CallRequestViewHolder(view);
        } else if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel message = messages.get(position);
        if (holder instanceof ProposalViewHolder) {
            ((ProposalViewHolder) holder).bind(message);
        } else if (holder instanceof CallRequestViewHolder) {
            ((CallRequestViewHolder) holder).bind(message);
        } else if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, callListener);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, callListener);
        }
    }

    private boolean isCallMessage(MessageModel message) {
        return "CALL".equals(message.getType()) || "call".equals(message.getType());
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class CallRequestViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvStatus;
        Button btnAccept, btnReject;
        LinearLayout layoutActions;

        CallRequestViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            layoutActions = itemView.findViewById(R.id.layoutActions);
        }

        void bind(MessageModel message) {
            tvMessage.setText(message.getMessage());
            tvStatus.setText("Status: " + (message.getProposalStatus() != null ? message.getProposalStatus() : "PENDING"));

            // Show actions only if PENDING and current user is the RECEIVER (CA)
            boolean isReceiver = message.getReceiverId().equals(currentUserId);
            boolean isPending = "PENDING".equals(message.getProposalStatus());

            if (isReceiver && isPending) {
                layoutActions.setVisibility(View.VISIBLE);
                btnAccept.setOnClickListener(v -> {
                    if (callListener != null) callListener.onRequestAccept(message);
                });
                btnReject.setOnClickListener(v -> {
                    if (callListener != null) callListener.onRequestReject(message);
                });
            } else {
                layoutActions.setVisibility(View.GONE);
            }
        }
    }

    class ProposalViewHolder extends RecyclerView.ViewHolder {
        TextView tvDescription, tvAmount, tvStatus;
        Button btnAccept, btnReject;
        LinearLayout layoutActions;

        ProposalViewHolder(View itemView) {
            super(itemView);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            layoutActions = itemView.findViewById(R.id.layoutActions);
        }

        void bind(MessageModel message) {
            tvDescription.setText(message.getProposalDescription());
            tvAmount.setText("Amount: ₹ " + message.getProposalAmount());
            tvStatus.setText("Status: " + message.getProposalStatus());

            // Show actions only if PENDING and current user is the RECEIVER
            boolean isReceiver = message.getReceiverId().equals(currentUserId);
            boolean isPending = "PENDING".equals(message.getProposalStatus());

            if (isReceiver && isPending) {
                layoutActions.setVisibility(View.VISIBLE);
                btnAccept.setOnClickListener(v -> {
                    if (proposalListener != null) proposalListener.onAccept(message);
                });
                btnReject.setOnClickListener(v -> {
                    if (proposalListener != null) proposalListener.onReject(message);
                });
            } else {
                layoutActions.setVisibility(View.GONE);
            }
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTimestamp, tvDocName;
        ImageView ivChatPreview;
        View cardPreview;
        LinearLayout layoutDocument;

        SentMessageViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvChatMessage);
            tvTimestamp = itemView.findViewById(R.id.tvChatTime);
            ivChatPreview = itemView.findViewById(R.id.ivChatPreview);
            cardPreview = itemView.findViewById(R.id.cardPreview);
            layoutDocument = itemView.findViewById(R.id.layoutDocument);
            tvDocName = itemView.findViewById(R.id.tvDocName);
        }

        void bind(MessageModel message, OnCallActionListener callListener) {
            // Reset visibilities
            tvMessage.setVisibility(View.GONE);
            cardPreview.setVisibility(View.GONE);
            if (layoutDocument != null) layoutDocument.setVisibility(View.GONE);

            if ("CALL".equals(message.getType()) || "call".equals(message.getType())) {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText("📞 Video Call Started");
                tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
                tvMessage.setTextColor(itemView.getContext().getResources().getColor(R.color.on_primary));
                tvMessage.setOnClickListener(v -> {
                     if (callListener != null) callListener.onJoinCall(message);
                });
            } else if ("DOCUMENT".equals(message.getType())) {
                if (layoutDocument != null) {
                    layoutDocument.setVisibility(View.VISIBLE);
                    String docName = "Document";
                    if (message.getMessage() != null && !message.getMessage().isEmpty() && !message.getMessage().equals("Attachment")) {
                        docName = message.getMessage();
                    }
                    if (tvDocName != null) tvDocName.setText(docName);
                    
                    layoutDocument.setOnClickListener(v -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse(message.getImageUrl()));
                            itemView.getContext().startActivity(intent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(itemView.getContext(), "Cannot open document", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    // Fallback if layoutDocument is missing (should not happen with updated xml)
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText("📄 View Document");
                }
            } else if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                cardPreview.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getImageUrl())
                        .into(ivChatPreview);
            } else {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(message.getMessage());
                tvMessage.setPaintFlags(tvMessage.getPaintFlags() & (~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvMessage.setTextColor(itemView.getContext().getResources().getColor(R.color.on_primary));
                tvMessage.setOnClickListener(null);
            }
            tvTimestamp.setText(formatTime(message.getTimestamp()));
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTimestamp, tvDocName;
        ImageView ivChatPreview;
        View cardPreview;
        LinearLayout layoutDocument;

        ReceivedMessageViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvChatMessage);
            tvTimestamp = itemView.findViewById(R.id.tvChatTime);
            ivChatPreview = itemView.findViewById(R.id.ivChatPreview);
            cardPreview = itemView.findViewById(R.id.cardPreview);
            layoutDocument = itemView.findViewById(R.id.layoutDocument);
            tvDocName = itemView.findViewById(R.id.tvDocName);
        }

        void bind(MessageModel message, OnCallActionListener callListener) {
            // Reset visibilities
            tvMessage.setVisibility(View.GONE);
            cardPreview.setVisibility(View.GONE);
            if (layoutDocument != null) layoutDocument.setVisibility(View.GONE);

            if ("CALL".equals(message.getType()) || "call".equals(message.getType())) {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText("📞 Incoming Video Call\nTap to Join");
                tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
                tvMessage.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_blue_dark));
                tvMessage.setOnClickListener(v -> {
                    if (callListener != null) callListener.onJoinCall(message);
                });
            } else if ("DOCUMENT".equals(message.getType())) {
                if (layoutDocument != null) {
                    layoutDocument.setVisibility(View.VISIBLE);
                    String docName = "Document";
                    if (message.getMessage() != null && !message.getMessage().isEmpty() && !message.getMessage().equals("Attachment")) {
                        docName = message.getMessage();
                    }
                    if (tvDocName != null) tvDocName.setText(docName);

                    layoutDocument.setOnClickListener(v -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse(message.getImageUrl()));
                            itemView.getContext().startActivity(intent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(itemView.getContext(), "Cannot open document", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText("📄 View Document");
                }
            } else if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                cardPreview.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getImageUrl())
                        .into(ivChatPreview);
            } else {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(message.getMessage());
                tvMessage.setPaintFlags(tvMessage.getPaintFlags() & (~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvMessage.setTextColor(itemView.getContext().getResources().getColor(android.R.color.black));
                tvMessage.setOnClickListener(null);
            }
            tvTimestamp.setText(formatTime(message.getTimestamp()));
        }
    }

    private static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
