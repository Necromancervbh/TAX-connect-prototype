package com.example.taxconnect.model;

public class MessageModel {
    private String senderId;
    private String receiverId;
    private String chatId; // Unique ID for the conversation (uid1_uid2 sorted)
    private String message;
    private long timestamp;
    private String type; // "TEXT" or "PROPOSAL"
    
    // Proposal specific fields
    private String proposalDescription;
    private String proposalAmount;
    private String proposalStatus; // "PENDING", "ACCEPTED", "REJECTED"
    private String id; // Firestore Document ID
    private String imageUrl; // For image messages

    public MessageModel() {}

    // Constructor for Text Message
    public MessageModel(String senderId, String receiverId, String chatId, String message, long timestamp, String type) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.chatId = chatId;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
    }
    
    // Constructor for Proposal Message
    public MessageModel(String senderId, String receiverId, String chatId, String message, long timestamp, String type, String description, String amount, String status) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.chatId = chatId;
        this.message = message; // Summary text like "Sent a proposal"
        this.timestamp = timestamp;
        this.type = type;
        this.proposalDescription = description;
        this.proposalAmount = amount;
        this.proposalStatus = status;
    }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getProposalDescription() { return proposalDescription; }
    public void setProposalDescription(String proposalDescription) { this.proposalDescription = proposalDescription; }

    public String getProposalAmount() { return proposalAmount; }
    public void setProposalAmount(String proposalAmount) { this.proposalAmount = proposalAmount; }

    public String getProposalStatus() { return proposalStatus; }
    public void setProposalStatus(String proposalStatus) { this.proposalStatus = proposalStatus; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
